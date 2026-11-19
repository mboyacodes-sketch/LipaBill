package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import java.io.File
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Diagnostics only (skipped by default). Runs a local inbox export through the import pipeline:
 *
 *   INBOX_AUDIT=/path/inbox.json ./gradlew :app:testPlayDebugUnitTest --tests '*InboxAudit*'
 *
 * Prints counts per sender and digit-masked samples of money-looking SMS that are dropped.
 * Never commit an export; it is personal data.
 */
class InboxAuditTest {

    private fun sqlLike(value: String, pattern: String): Boolean {
        val regex = pattern.split("%").joinToString(".*") { Regex.escape(it) }
        return Regex("^$regex$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matches(value)
    }

    private fun mask(body: String) = body.replace(Regex("\\d"), "#").replace(Regex("\\s+"), " ").take(150)

    @Test
    fun audit() {
        val path = System.getenv("INBOX_AUDIT")
        assumeTrue(!path.isNullOrBlank())
        val region = AppRegion.valueOf(System.getenv("INBOX_REGION") ?: "KENYA")
        val enabled = region.sources
        val rows = JSONArray(File(path!!).readText())
        val (bodySel, bodyArgs) = SmsInboxReader.bodyQuery(region)
        val addressLikes = enabled.flatMap { it.addressLikes }.distinct()
        data class Tally(var total: Int = 0, var source: String = "-", var sqlAddr: Int = 0, var sqlBody: Int = 0,
                         var accepted: Int = 0, var parsed: Int = 0, var failed: Int = 0, val samples: MutableList<String> = mutableListOf())
        val bySender = linkedMapOf<String, Tally>()
        val moneyHint = Regex("(?i)(ksh|kes|confirmed|credited|debited|received|transaction|trans id|tid)")
        for (i in 0 until rows.length()) {
            val o = rows.getJSONObject(i)
            val address = o.getString("address")
            val body = o.getString("body")
            val t = bySender.getOrPut(address) { Tally() }
            t.total++
            val source = MoneySource.fromSender(address, region)
            t.source = source?.name ?: "-"
            val addrOk = addressLikes.any { sqlLike(address, it) }
            if (addrOk) t.sqlAddr++
            val bodyOk = bodyArgs.any { sqlLike(body, it) }
            if (addrOk && bodyOk) t.sqlBody++
            val outcome = ConfirmationIngestion.acceptOutcome(address, body, o.getLong("date"), region, enabled)
            when (outcome) {
                is ConfirmationIngestion.Outcome.Matched -> { t.accepted++; t.parsed++ }
                is ConfirmationIngestion.Outcome.ParseFailed -> { t.accepted++; t.failed++ }
                ConfirmationIngestion.Outcome.Skipped -> Unit
            }
            val passedSql = addrOk && bodyOk
            val kept = outcome is ConfirmationIngestion.Outcome.Matched && passedSql
            if (!kept && moneyHint.containsMatchIn(body) && t.samples.size < 4) {
                val why = when {
                    source == null -> "no-source"
                    !addrOk -> "sql-address"
                    !bodyOk -> "sql-body"
                    outcome is ConfirmationIngestion.Outcome.ParseFailed -> "parse-failed"
                    else -> "not-confirmation"
                }
                t.samples += "[$why] ${mask(body)}"
            }
        }
        simulateImport(rows, region, enabled)
        rejectedPatterns(rows, region, enabled)
        newlyAccepted(rows, region, enabled)
        println("sender | total | source | sqlAddr | sqlAddr+Body | accepted | parsed | parseFailed")
        bySender.entries.filter { it.value.total >= 5 && !it.key.matches(Regex("\\+?\\d{6,}")) }
            .sortedByDescending { it.value.total }
            .forEach { (sender, t) ->
                println("$sender | ${t.total} | ${t.source} | ${t.sqlAddr} | ${t.sqlBody} | ${t.accepted} | ${t.parsed} | ${t.failed}")
                t.samples.forEach { println("    $it") }
            }
    }

    /** Mirrors TransactionRepository.importNewConfirmations on an empty ledger. */
    private fun simulateImport(rows: JSONArray, region: AppRegion, enabled: Set<MoneySource>) {
        val sms = (0 until rows.length()).map { rows.getJSONObject(it) }.sortedByDescending { it.getLong("date") }
        val known = HashSet<String>()
        val twinCodes = HashSet<String>()
        fun twin(b: String) = com.lipabill.app.data.parser.KenyanConfirmationParser.isSettlementTwinSms(b)
        val keptBodyBySourceCode = HashMap<String, String>()
        val pending = ArrayList<com.lipabill.app.data.model.MpesaTransaction>()
        val shown = HashMap<MoneySource, Int>()
        val dropped = linkedMapOf<String, MutableMap<String, Int>>()
        val samples = linkedMapOf<String, MutableList<String>>()
        fun drop(source: MoneySource, why: String, body: String) {
            dropped.getOrPut(source.name) { linkedMapOf() }.merge(why, 1, Int::plus)
            val list = samples.getOrPut("${source.name}/$why") { mutableListOf() }
            if (list.size < 3) list += mask(body)
        }
        for (o in sms) {
            val address = o.getString("address"); val body = o.getString("body"); val date = o.getLong("date")
            val source = MoneySource.fromSender(address, region) ?: continue
            if (source !in enabled) continue
            val parsed = ConfirmationIngestion.acceptAndParse(address, body, date, region, enabled) ?: continue
            val lipaNaKcb = com.lipabill.app.data.parser.KenyanConfirmationParser.isLipaNaKcbMerchantSms(body)
            val twin = com.lipabill.app.data.parser.KenyanConfirmationParser.isSettlementTwinSms(body)
            val quick = if (source == MoneySource.MPESA && region == AppRegion.KENYA && !lipaNaKcb) {
                com.lipabill.app.data.parser.MpesaSmsParser.receiptCode(body)
            } else {
                com.lipabill.app.data.parser.KenyanConfirmationParser.receiptCode(body)
            }
            if (quick != null && "${source.name}:$quick" in known && lipaNaKcb && quick in twinCodes) {
                known.remove("${source.name}:$quick")
                pending.removeAll { it.source == source && twin(it.rawBody) && quick in com.lipabill.app.data.parser.KenyanConfirmationParser.allReceiptCodes(it.rawBody) }
            }
            if (quick != null && "${source.name}:$quick" in known) {
                val prior = keptBodyBySourceCode["${source.name}:$quick"]
                drop(source, if (prior == body) "exact-duplicate" else "code-collision", body)
                if (prior != null && prior != body) println("  COLLISION ${source.name} code=${quick.replace(Regex("\\d"), "#")}\n     kept: ${mask(prior)}\n     lost: ${mask(body)}")
                continue
            }
            if (twin && ConfirmationIngestion.sharesKnownReceipt(body, known)) { drop(source, "settlement-twin", body); continue }
            val key = ConfirmationIngestion.sourceKey(parsed)
            if (!known.add(key)) {
                drop(source, if (keptBodyBySourceCode[key] == body) "exact-duplicate" else "code-collision", body); continue
            }
            keptBodyBySourceCode[key] = body
            for (code in com.lipabill.app.data.parser.KenyanConfirmationParser.allReceiptCodes(body)) {
                known.add("${source.name}:$code"); keptBodyBySourceCode.putIfAbsent("${source.name}:$code", body)
            }
            pending += parsed
            if (twin) twinCodes += com.lipabill.app.data.parser.KenyanConfirmationParser.allReceiptCodes(body)
            if (source in setOf(MoneySource.NCBA, MoneySource.EQUITY, MoneySource.STANCHART) && shown.merge(source, 1, Int::plus)!! <= 4) {
                println("  PARSED ${source.name} ${parsed.type} amt=${parsed.amount} party=${if (parsed.counterpartyName.isNullOrBlank()) "-" else "named"} cost=${parsed.cost} bal=${parsed.balance} :: ${mask(body).take(80)}")
            }
        }
        val stored = ConfirmationIngestion.dedupeLipaNaKcbTwins(pending)
        println("== simulated import: stored rows per source ==")
        stored.groupingBy { it.source }.eachCount().toList().sortedByDescending { it.second }.forEach { println("  ${it.first} ${it.second}") }
        println("  lipa-na-kcb twins removed: ${pending.size - stored.size}")
        println("== dropped after parsing ==")
        dropped.forEach { (src, m) -> println("  $src $m") }
        samples.forEach { (k, v) -> println("  [$k]"); v.forEach { println("      $it") } }
    }

    /** Every mapped-source SMS the confirmation gate skips, grouped by its masked opening. */
    private fun rejectedPatterns(rows: JSONArray, region: AppRegion, enabled: Set<MoneySource>) {
        val groups = linkedMapOf<String, Int>()
        for (i in 0 until rows.length()) {
            val o = rows.getJSONObject(i)
            val address = o.getString("address"); val body = o.getString("body")
            val source = MoneySource.fromSender(address, region) ?: continue
            val outcome = ConfirmationIngestion.acceptOutcome(address, body, o.getLong("date"), region, enabled)
            if (outcome is ConfirmationIngestion.Outcome.Matched) continue
            val key = "${source.name} | ${if (outcome is ConfirmationIngestion.Outcome.ParseFailed) "PARSE" else "SKIP"} | " +
                mask(body).take(70)
            groups.merge(key, 1, Int::plus)
        }
        println("== rejected by gate (source | why | opening) ==")
        groups.entries.sortedByDescending { it.value }.forEach { println("  ${it.value}  ${it.key}") }
    }

    /** SMS the widened bank gate accepts that the old verb list did not. */
    private fun newlyAccepted(rows: JSONArray, region: AppRegion, enabled: Set<MoneySource>) {
        val old = Regex("\\b(credited|debited|sent|received|paid|withdraw|deposit|transferred|reversed|purchase[d]?|bought|processed)\\b", RegexOption.IGNORE_CASE)
        val groups = linkedMapOf<String, Int>()
        for (i in 0 until rows.length()) {
            val o = rows.getJSONObject(i)
            val address = o.getString("address"); val body = o.getString("body")
            val source = MoneySource.fromSender(address, region) ?: continue
            if (source == MoneySource.MPESA) continue
            if (old.containsMatchIn(body) || body.contains("Confirmed", ignoreCase = true)) continue
            val outcome = ConfirmationIngestion.acceptOutcome(address, body, o.getLong("date"), region, enabled)
            if (outcome !is ConfirmationIngestion.Outcome.Matched) continue
            groups.merge("${source.name} ${outcome.tx.type} | ${mask(body).take(80)}", 1, Int::plus)
        }
        println("== newly rejected (old gate accepted) ==")
        val oldOtp = Regex("\\b(otp|verification code|one[- ]time(?:\\s+password)?|do not share this code|pin is)\\b", RegexOption.IGNORE_CASE)
        val lost = linkedMapOf<String, Int>()
        for (i in 0 until rows.length()) {
            val o = rows.getJSONObject(i)
            val address = o.getString("address"); val body = o.getString("body")
            val source = MoneySource.fromSender(address, region) ?: continue
            if (source == MoneySource.MPESA) continue
            val oldOk = !oldOtp.containsMatchIn(body) && region.matchesCurrency(body) &&
                (old.containsMatchIn(body) || body.contains("Confirmed", ignoreCase = true))
            if (!oldOk) continue
            if (ConfirmationIngestion.acceptOutcome(address, body, o.getLong("date"), region, enabled) is ConfirmationIngestion.Outcome.Matched) continue
            lost.merge("${source.name} | ${mask(body).take(110)}", 1, Int::plus)
        }
        lost.entries.sortedByDescending { it.value }.take(25).forEach { println("  ${it.value}  ${it.key}") }
        println("== newly accepted ==")
        groups.entries.sortedByDescending { it.value }.forEach { println("  ${it.value}  ${it.key}") }
    }
}
