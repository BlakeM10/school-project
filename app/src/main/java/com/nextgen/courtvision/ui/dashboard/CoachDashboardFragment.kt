package com.nextgen.courtvision.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.databinding.FragmentCoachDashboardBinding
import com.nextgen.courtvision.viewmodel.AuthViewModel

/**
 * Coach home screen. Placeholder scaffold — roster and trend charts are built in
 * Phase 6; this destination exists now as the post-login routing target.
 */
class CoachDashboardFragment : Fragment() {

    private var _binding: FragmentCoachDashboardBinding? = null
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
        _binding = FragmentCoachDashboardBinding.inflate(inflater, container, false)
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
