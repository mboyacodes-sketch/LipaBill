/**
 * Browser port of com.lipabill.app.pay.PayLink.parse (v1–v3).
 * Keep in lockstep with app/src/main/java/com/lipabill/app/pay/PayLink.kt
 * and web/p/fixtures.json (asserted by Kotlin + Node tests).
 */
(function (root) {
  "use strict";

  var ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  var MIX_KEY = [0x4c, 0x69, 0x70, 0x61, 0x42, 0x69, 0x6c, 0x6c, 0x50];
  var VERSION_V1 = 1;
  var VERSION_V2 = 2;
  var VERSION_V3 = 3;
  var WIDTH_V1 = 11;
  var WIDTH_V2 = 13;
  var WIDTH_V3 = 27;
  var MAX_AMOUNT = 0xffffff;
  var FIXED_DAY = 0xffff;
  var DAY_MS = 86400000;
  var DAY_EPOCH_MS = 1704067200000;
  var KIND_PHONE = 0;
  var KIND_TILL = 1;
  var KIND_PAYBILL = 2;

  function dayIndex(nowMillis) {
    var day = Math.floor((nowMillis - DAY_EPOCH_MS) / DAY_MS);
    if (day < 0) day = 0;
    if (day > FIXED_DAY - 1) day = FIXED_DAY - 1;
    return day;
  }

  function mix(data) {
    var out = new Uint8Array(data.length);
    for (var i = 0; i < data.length; i++) {
      out[i] = data[i] ^ MIX_KEY[i % MIX_KEY.length];
    }
    return out;
  }

  function decodeBytes(token, width) {
    var value = 0n;
    var base = 62n;
    for (var i = 0; i < token.length; i++) {
      var idx = ALPHABET.indexOf(token.charAt(i));
      if (idx < 0) return null;
      value = value * base + BigInt(idx);
    }
    var hex = value.toString(16);
    if (hex.length % 2) hex = "0" + hex;
    var stripped = new Uint8Array(hex.length / 2);
    for (var h = 0; h < stripped.length; h++) {
      stripped[h] = parseInt(hex.substr(h * 2, 2), 16);
    }
    if (stripped.length > width) return null;
    var packed = new Uint8Array(width);
    packed.set(stripped, width - stripped.length);
    return packed;
  }

  function checksum(packed, until) {
    var sum = 0;
    for (var i = 0; i < until; i++) sum ^= packed[i];
    return sum & 0xff;
  }

  function dayOk(packed, dayAt, nowMillis) {
    var day = ((packed[dayAt] & 0xff) << 8) | (packed[dayAt + 1] & 0xff);
    if (day === FIXED_DAY) return true;
    var today = dayIndex(nowMillis);
    return day === today || day === today - 1;
  }

  function readAmount(packed, at) {
    return (
      ((packed[at] & 0xff) << 16) |
      ((packed[at + 1] & 0xff) << 8) |
      (packed[at + 2] & 0xff)
    );
  }

  function readBcd(packed, start, digitCount) {
    var out = "";
    for (var i = 0; i < digitCount / 2; i++) {
      var b = packed[start + i] & 0xff;
      var hi = b >> 4;
      var lo = b & 0x0f;
      if (hi > 9 || lo > 9) return "";
      out += String(hi) + String(lo);
    }
    return out;
  }

  function localKenyanFromInternational(digits) {
    if (digits.indexOf("254") === 0 && digits.length === 12) {
      return "0" + digits.slice(3);
    }
    return null;
  }

  function canonicalPhone(raw) {
    if (!raw) return null;
    var digits = String(raw).replace(/\D/g, "");
    var local =
      localKenyanFromInternational(digits) ||
      (digits.length === 9 && digits.charAt(0) >= "1" && digits.charAt(0) <= "9"
        ? "0" + digits
        : null);
    if (!local && digits.length === 10 && digits.charAt(0) === "0") local = digits;
    if (!local || local.length !== 10) return null;
    if (local.charAt(1) !== "1" && local.charAt(1) !== "7") return null;
    return local;
  }

  function canonicalMerchantId(raw) {
    if (!raw) return null;
    var digits = String(raw).replace(/\D/g, "");
    if (digits.length < 5 || digits.length > 12) return null;
    return digits;
  }

  function canonicalAccount(raw) {
    if (!raw) return null;
    var clean = String(raw).replace(/[^A-Za-z0-9]/g, "");
    if (clean.length < 1 || clean.length > 12) return null;
    return clean;
  }

  function phoneTarget(packed, phoneStart, amountAt) {
    var digits = readBcd(packed, phoneStart, 12);
    var phone = canonicalPhone(digits);
    if (!phone) return null;
    var amount = readAmount(packed, amountAt);
    if (amount > MAX_AMOUNT) return null;
    return {
      kind: "PHONE",
      identifier: phone,
      account: null,
      amountShillings: amount > 0 ? amount : null
    };
  }

  function decodeV3(token, nowMillis) {
    var mixed = decodeBytes(token, WIDTH_V3);
    if (!mixed) return null;
    var packed = mix(mixed);
    if ((packed[0] & 0xff) !== VERSION_V3) return null;
    if ((packed[26] & 0xff) !== checksum(packed, 26)) return null;
    if (!dayOk(packed, 8, nowMillis)) return null;
    var kindByte = packed[1] & 0xff;
    var idDigits = readBcd(packed, 2, 12);
    if (idDigits.length !== 12) return null;
    var amount = readAmount(packed, 10);
    if (amount > MAX_AMOUNT) return null;
    var accountLen = packed[13] & 0xff;
    if (accountLen > 12) return null;
    var account = "";
    if (accountLen > 0) {
      var chars = [];
      for (var i = 0; i < accountLen; i++) {
        chars.push(String.fromCharCode(packed[14 + i]));
      }
      account = chars.join("");
    }
    if (kindByte === KIND_PHONE) {
      if (accountLen !== 0) return null;
      var phone = canonicalPhone(idDigits);
      if (!phone) return null;
      return {
        kind: "PHONE",
        identifier: phone,
        account: null,
        amountShillings: amount > 0 ? amount : null
      };
    }
    if (kindByte === KIND_TILL) {
      if (accountLen !== 0) return null;
      var till = canonicalMerchantId(idDigits.replace(/^0+/, "") || "0");
      if (!till) return null;
      return {
        kind: "TILL",
        identifier: till,
        account: null,
        amountShillings: amount > 0 ? amount : null
      };
    }
    if (kindByte === KIND_PAYBILL) {
      var business = canonicalMerchantId(idDigits.replace(/^0+/, "") || "0");
      var acc = canonicalAccount(account);
      if (!business || !acc) return null;
      return {
        kind: "PAYBILL",
        identifier: business,
        account: acc,
        amountShillings: amount > 0 ? amount : null
      };
    }
    return null;
  }

  function decodeV2(token, nowMillis) {
    var mixed = decodeBytes(token, WIDTH_V2);
    if (!mixed) return null;
    var packed = mix(mixed);
    if ((packed[0] & 0xff) !== VERSION_V2) return null;
    if ((packed[12] & 0xff) !== checksum(packed, 12)) return null;
    if (!dayOk(packed, 7, nowMillis)) return null;
    return phoneTarget(packed, 1, 9);
  }

  function decodeV1(token) {
    var mixed = decodeBytes(token, WIDTH_V1);
    if (!mixed) return null;
    var packed = mix(mixed);
    if ((packed[0] & 0xff) !== VERSION_V1) return null;
    if ((packed[10] & 0xff) !== checksum(packed, 10)) return null;
    return phoneTarget(packed, 1, 7);
  }

  function tokenFrom(raw) {
    var trimmed = (raw || "").trim();
    if (!trimmed) return null;
    var lower = trimmed.toLowerCase();
    var body = null;
    var markers = [
      "lipabill://p/",
      "https://lipabill.app/p/",
      "http://lipabill.app/p/"
    ];
    for (var m = 0; m < markers.length; m++) {
      if (lower.indexOf(markers[m]) === 0) {
        body = trimmed.substring(markers[m].length);
        break;
      }
    }
    if (body == null) {
      if (lower.indexOf("://") >= 0 || lower.indexOf("/") >= 0 || lower.indexOf(".") >= 0) {
        return null;
      }
      body = trimmed;
    }
    var clean = body.split("?")[0].split("#")[0].replace(/^\/+|\/+$/g, "").split("/")[0];
    if (clean.length < 8 || clean.length > 40) return null;
    for (var i = 0; i < clean.length; i++) {
      if (ALPHABET.indexOf(clean.charAt(i)) < 0) return null;
    }
    return clean;
  }

  function parse(raw, nowMillis) {
    var token = tokenFrom(raw);
    if (!token) return null;
    var now = nowMillis == null ? Date.now() : nowMillis;
    return (
      decodeV3(token, now) ||
      decodeV2(token, now) ||
      decodeV1(token)
    );
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var area = document.createElement("textarea");
      area.value = text;
      document.body.appendChild(area);
      area.select();
      try {
        document.execCommand("copy");
        resolve();
      } catch (e) {
        reject(e);
      } finally {
        document.body.removeChild(area);
      }
    });
  }

  // Official Safaricom M-PESA app on Play (Lifestyle).
  var MPESA_PACKAGE = "com.safaricom.mpesa.lifestyle";

  function openMpesa() {
    var fallback =
      "https://play.google.com/store/apps/details?id=" + MPESA_PACKAGE;
    var intent =
      "intent://#Intent;package=" +
      MPESA_PACKAGE +
      ";S.browser_fallback_url=" +
      encodeURIComponent(fallback) +
      ";end";
    window.location.href = intent;
  }

  function openLipaBill(token) {
    window.location.href = "lipabill://p/" + token;
  }

  function getLipaBill() {
    window.location.href =
      "https://play.google.com/store/apps/details?id=com.lipabill.app";
  }

  root.PayLinkWeb = {
    parse: parse,
    tokenFrom: tokenFrom,
    copyText: copyText,
    openMpesa: openMpesa,
    openLipaBill: openLipaBill,
    getLipaBill: getLipaBill
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = root.PayLinkWeb;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
