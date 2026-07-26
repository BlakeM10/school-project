package com.nextgen.courtvision.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentRegisterBinding
import com.nextgen.courtvision.domain.model.Role
import com.nextgen.courtvision.viewmodel.AuthUiState
import com.nextgen.courtvision.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Two modes:
 *  - registration: name + email + password + role
 *  - profile completion (first-time Google sign-in): name + role only
 */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val completeProfileMode: Boolean
        get() = arguments?.getBoolean(ARG_COMPLETE_PROFILE_MODE) == true

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
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (completeProfileMode) {
            binding.title.setText(R.string.register_complete_profile_title)
            binding.layoutEmail.isVisible = false
            binding.layoutPassword.isVisible = false
            binding.buttonRegister.setText(R.string.register_complete_profile_button)
        }

        binding.buttonRegister.setOnClickListener {
            val name = binding.inputName.text?.toString().orEmpty()
            val role = selectedRole()
            if (completeProfileMode) {
                authViewModel.completeProfile(name, role)
            } else {
                authViewModel.register(
                    displayName = name,
                    email = binding.inputEmail.text?.toString().orEmpty(),
                    password = binding.inputPassword.text?.toString().orEmpty(),
                    role = role,
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collect { state ->
                    binding.progress.isVisible = state is AuthUiState.Loading
                    binding.buttonRegister.isEnabled = state !is AuthUiState.Loading
                    when (state) {
                        is AuthUiState.NeedsProfile -> {
                            if (binding.inputName.text.isNullOrBlank() && state.suggestedName.isNotBlank()) {
                                binding.inputName.setText(state.suggestedName)
                            }
                        }
                        is AuthUiState.Error -> {
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            authViewModel.acknowledgeError()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun selectedRole(): Role? = when (binding.roleToggle.checkedButtonId) {
        R.id.buttonRolePlayer -> Role.PLAYER
        R.id.buttonRoleCoach -> Role.COACH
        else -> null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_COMPLETE_PROFILE_MODE = "completeProfileMode"
    }
}
