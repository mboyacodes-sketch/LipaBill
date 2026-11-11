package com.lipabill.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayeeNamesTest {

    @Test
    fun fold_payee_maps_dial_placeholders() {
        assertEquals("bill:888880", foldPayeeName("Paybill 888880"))
        assertEquals("till:5629921", foldPayeeName("Till 5629921"))
        assertEquals("pochi:0712345678", foldPayeeName("Pochi 0712345678"))
        assertEquals("pochi:0712345678", foldPayeeName("Pochi la Biashara 0712345678"))
    }

    @Test
    fun fold_payee_strips_branch_suffixes_but_keeps_china_town() {
        assertEquals("java house", foldPayeeName("Java House Westlands"))
        assertEquals("java house", foldPayeeName("Java House Kilimani Mall"))
        assertEquals("china town", foldPayeeName("China Town"))
    }

    @Test
    fun fold_merchant_collapses_whitespace_and_rejects_blank() {
        assertEquals("java house", foldMerchantName("  Java   House  "))
        assertNull(foldMerchantName(null))
        assertNull(foldMerchantName("   "))
    }
}
