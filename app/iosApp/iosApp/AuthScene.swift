import SwiftUI

struct AuthScene: View {
    let model: AccountViewModel
    let onDone: () -> Void
    @State private var email = ""
    @State private var password = ""
    @State private var signup = false

    var body: some View {
        AuthScreen(
            state: model.state,
            email: $email,
            password: $password,
            signup: signup,
            onSwitchMode: { signup.toggle(); password = "" },
            onGoogle: {
                password = ""
                Task {
                    if await model.signInWithGoogle() { onDone() }
                }
            },
            onSubmit: {
                Task {
                    if await model.authenticate(email: email, password: password, signup: signup) {
                        password = ""
                        onDone()
                    }
                }
            },
            onGuest: { password = ""; onDone() }
        )
        .onDisappear { password = "" }
    }
}
