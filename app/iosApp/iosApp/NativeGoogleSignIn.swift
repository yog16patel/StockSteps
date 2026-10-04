import UIKit
#if canImport(FirebaseCore)
import FirebaseCore
import FirebaseAuth
import GoogleSignIn
#endif

// Native presentation and provider tokens remain inside the platform adapter.
enum NativeGoogleSignIn {
    static func handle(_ url: URL) {
        #if canImport(FirebaseCore)
        _ = GIDSignIn.sharedInstance.handle(url)
        #endif
    }
    #if canImport(FirebaseCore)
    static func signIn(completion: @escaping (AuthCredential?, String?) -> Void) {
        DispatchQueue.main.async {
            guard let clientID = FirebaseApp.app()?.options.clientID, !clientID.isEmpty else {
                completion(nil, "Google sign-in needs an updated Firebase app configuration.")
                return
            }
            guard let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
                  var presenter = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
                completion(nil, "Reopen the login screen and try again.")
                return
            }
            while let presented = presenter.presentedViewController { presenter = presented }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                if let error {
                    let cancelled = (error as NSError).code == GIDSignInError.canceled.rawValue
                    completion(nil, cancelled ? "Google sign-in was cancelled." : "Could not sign in with Google. Check your connection and try again.")
                    return
                }
                guard let user = result?.user, let token = user.idToken?.tokenString, !token.isEmpty else {
                    completion(nil, "Google did not return a valid sign-in credential.")
                    return
                }
                completion(GoogleAuthProvider.credential(withIDToken: token, accessToken: user.accessToken.tokenString), nil)
            }
        }
    }
    #endif
}
