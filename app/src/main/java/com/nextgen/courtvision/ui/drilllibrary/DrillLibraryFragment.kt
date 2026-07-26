package com.nextgen.courtvision.ui.drilllibrary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.databinding.FragmentDrillLibraryBinding
import com.nextgen.courtvision.viewmodel.AuthViewModel

/**
 * Player home screen. Placeholder scaffold — the drill catalogue UI is built in
 * Phase 6; this destination exists now as the post-login routing target.
 */
class DrillLibraryFragment : Fragment() {

    private var _binding: FragmentDrillLibraryBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels {
        AuthViewModel.Factory(
            (requireActivity().application as CourtVisionApp).container.authRepository,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDrillLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSignOut.setOnClickListener { authViewModel.signOut() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
