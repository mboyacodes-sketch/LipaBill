package com.lipabill.app.ui.payment

import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.ui.pay.PayMoneyBottomSheetFragment
import com.lipabill.app.ui.send.SendMoneyBottomSheetFragment

/** Fragment-manager helpers for the Send / Pay money sheets. */
object MoneySheets {

    fun dismiss(fm: FragmentManager) {
        listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG).forEach { tag ->
            (fm.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }

    fun isSendShowing(fm: FragmentManager): Boolean =
        fm.findFragmentByTag(SendMoneyBottomSheetFragment.TAG) != null

    fun isPayShowing(fm: FragmentManager): Boolean =
        fm.findFragmentByTag(PayMoneyBottomSheetFragment.TAG) != null

    fun showSend(fm: FragmentManager) {
        if (isSendShowing(fm)) return
        SendMoneyBottomSheetFragment.newInstance().show(fm, SendMoneyBottomSheetFragment.TAG)
    }

    fun showPay(fm: FragmentManager) {
        if (isPayShowing(fm)) return
        PayMoneyBottomSheetFragment.newInstance().show(fm, PayMoneyBottomSheetFragment.TAG)
    }
}
