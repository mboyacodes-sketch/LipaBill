package com.lipabill.app.data.tickets

/** East African airports this app treats as real IATA codes, not city-name fragments. */
internal val KNOWN_IATA = setOf(
    "NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL", "WIL"
)
