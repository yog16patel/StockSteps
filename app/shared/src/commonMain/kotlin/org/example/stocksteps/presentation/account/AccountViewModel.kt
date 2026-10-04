package org.example.stocksteps.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.domain.AccountException

internal data class AccountActionState(val busy: Boolean = false, val error: String? = null, val completed: Boolean = false)

internal class AccountViewModel(
    private val auth: AuthRepository,
    private val signIn: SignIn,
    private val signUp: SignUp,
    private val signOut: SignOut
) : ViewModel() {
    val session = auth.session
    private val mutableAction = MutableStateFlow(AccountActionState())
    val action = mutableAction.asStateFlow()

    fun submit(email: String, password: String, signup: Boolean) = runAction {
        if (signup) signUp(email, password) else signIn(email, password)
    }
    fun googleSignIn() = runAction { auth.signInWithGoogle() }
    fun logout() = runAction { signOut() }
    private fun runAction(block: suspend () -> Unit) {
        if (action.value.busy) return
        mutableAction.value = AccountActionState(busy = true)
        viewModelScope.launch {
            try {
                block()
                mutableAction.value = AccountActionState(completed = true)
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableAction.value = AccountActionState(error = if (cause is AccountException) cause.message else "Could not complete the account request. Try again.")
            }
        }
    }
}
