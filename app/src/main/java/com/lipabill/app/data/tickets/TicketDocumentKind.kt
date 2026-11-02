package com.lipabill.app.data.tickets

/**
 * What the user is adding / attaching.
 *
 * Add-ticket UI currently exposes [EVENT] and SGR SMS.
 * [SGR_TICKET] is for attaching a boarding QR on an existing SGR booking.
 */
enum class TicketDocumentKind {
    /** Concert / event QR — one upload, done (no boarding-pass step). */
    EVENT,

    /** SGR / Madaraka printed ticket (attach only, after SMS confirmation). */
    SGR_TICKET,

    /** Apple Wallet .pkpass — treated as a complete event ticket when shared in. */
    PKPASS,

    /** Heuristic fallback (prefer explicit kinds). */
    AUTO
}
