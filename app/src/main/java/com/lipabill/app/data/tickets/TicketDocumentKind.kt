package com.lipabill.app.data.tickets

/**
 * What the user is adding / attaching.
 *
 * Add-ticket UI currently exposes [EVENT] and SGR SMS only (flight / scan / manual paused).
 * [BOARDING_PASS] and [SGR_TICKET] are for attaching within an existing travel ticket.
 */
enum class TicketDocumentKind {
    /** Concert / event QR — one upload, done (no boarding-pass step). */
    EVENT,

    /** Airline e-ticket / itinerary — creates booking; boarding pass added later on the ticket. */
    FLIGHT_E_TICKET,

    /** Physical flight boarding pass (attach only). */
    BOARDING_PASS,

    /** SGR / Madaraka printed ticket (attach only, after SMS confirmation). */
    SGR_TICKET,

    /** Apple Wallet .pkpass — treated as a complete event ticket when shared in. */
    PKPASS,

    /** Heuristic fallback (prefer explicit kinds). */
    AUTO
}
