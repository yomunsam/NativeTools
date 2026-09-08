package app.yomu.netbar.diagnosis

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import app.yomu.netbar.R
import app.yomu.netbar.databinding.FragmentDiagnosisBinding
import app.yomu.netbar.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 诊断页 — same process as the rest of the app (no :netspeed IPC). */
class DiagnosisFragment : Fragment(R.layout.fragment_diagnosis) {

    private val binding by viewBinding(FragmentDiagnosisBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.progressCircular.isVisible = true
        collectionNow()
    }

    private fun collectionNow() {
        lifecycleScope.launchWhenCreated {
            val result = withContext(Dispatchers.IO) { Logic.collectionDiagnosis() }
            setData(result)
        }
    }

    private fun setData(data: String?) {
        binding.tvDiagnosisMsg.text = data
        binding.progressCircular.isGone = true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_diagnosis, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val result = binding.tvDiagnosisMsg.text.toString()
        return when (item.itemId) {
            R.id.action_copy -> {
                if (result.isNotEmpty()) {
                    requireContext().copy(result)
                }
                true
            }
            R.id.action_share -> {
                if (result.isNotEmpty()) {
                    requireContext().share(result)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
