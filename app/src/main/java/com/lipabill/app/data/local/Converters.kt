package com.lipabill.app.data.local

import androidx.room.TypeConverter
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ussd.RepeatOutcome

class Converters {
    @TypeConverter
    fun fromType(type: TransactionType): String = type.name

    @TypeConverter
    fun toType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.UNKNOWN)

    @TypeConverter
    fun fromOutcome(outcome: RepeatOutcome): String = outcome.name

    @TypeConverter
    fun toOutcome(value: String): RepeatOutcome =
        runCatching { RepeatOutcome.valueOf(value) }.getOrDefault(RepeatOutcome.ABORTED_ERROR)

    @TypeConverter
    fun fromTicketStatus(status: TicketStatus): String = status.name

    @TypeConverter
    fun toTicketStatus(value: String): TicketStatus =
        when (value) {
            "EXPIRED" -> TicketStatus.PAST_DUE // legacy
            else -> runCatching { TicketStatus.valueOf(value) }.getOrDefault(TicketStatus.ACTIVE)
        }

    @TypeConverter
    fun fromTicketSource(source: TicketSource): String = source.name

    @TypeConverter
    fun toTicketSource(value: String): TicketSource =
        runCatching { TicketSource.valueOf(value) }.getOrDefault(TicketSource.MANUAL_PASTE)

    @TypeConverter
    fun fromBarcodeFormat(format: TicketBarcodeFormat): String = format.name

    @TypeConverter
    fun toBarcodeFormat(value: String): TicketBarcodeFormat =
        runCatching { TicketBarcodeFormat.valueOf(value) }.getOrDefault(TicketBarcodeFormat.QR_CODE)
}
