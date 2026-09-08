package app.yomu.netbar.beta

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import app.yomu.netbar.R
import app.yomu.netbar.databinding.ItemBottomSheetListBinding
import app.yomu.netbar.ui.BottomSheetListFragment
import app.yomu.netbar.util.browse
import app.yomu.netbar.util.copy

class BetaFragment : BottomSheetListFragment<BetaFragment.Beta>() {

    data class Beta(
        @DrawableRes val resId: Int,
        @StringRes val textId: Int,
        @StringRes val urlId: Int
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.isGone = true
        binding.tvMessage.isGone = true
        setData(
            listOf(
                Beta(R.drawable.ic_logo_pgyer, R.string.label_pgyer, R.string.url_pgyer),
                Beta(
                    R.drawable.ic_logo_firebase,
                    R.string.label_firebase,
                    R.string.url_firebase_app_distribution
                ),
            )
        )
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun onBindViewHolder(binding: ItemBottomSheetListBinding, position: Int, beta: Beta) {
        binding.ivLogo.setImageResource(beta.resId)
        binding.tvTitle.setText(beta.textId)
        binding.root.setOnClickListener { requireContext().browse(beta.urlId) }
        binding.root.setOnLongClickListener {
            requireContext().copy(beta.urlId)
            return@setOnLongClickListener true
        }
    }
}
