package org.example.stocksteps.practice

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.example.stocksteps.userdata.UserAuthenticator
import org.example.stocksteps.userdata.user

/**
 * Practice Portfolio API. Signed-in only; the owner comes from the verified token, never from the
 * request, so one user can't read or change another's simulation. Clients never send prices, cash,
 * holding counts, plan flags or trial dates; the server computes all of them.
 */
fun Route.practiceRoutes(auth: UserAuthenticator, service: PracticeService, mock: Boolean) {
    route("/api/v1/me/practice") {
        get { user(auth) { uid -> call.respond(service.overview(uid)) } }
        post { user(auth) { uid -> call.respond(service.overview(uid)) } }
        get("/entitlement") { user(auth) { uid -> call.respond(service.entitlement(uid)) } }
        post("/trial/activate") { user(auth) { uid -> call.respond(service.activateTrial(uid)) } }
        post("/notices/trial-expired") { user(auth) { uid -> service.acknowledgeTrialNotice(uid); call.respond(service.entitlement(uid)) } }
        get("/transactions") { user(auth) { uid -> call.respond(service.transactions(uid, call.request.queryParameters["type"])) } }
        get("/performance") { user(auth) { uid -> call.respond(service.performance(uid, call.request.queryParameters["range"])) } }
        post("/orders/preview") { user(auth) { uid -> call.respond(service.preview(uid, call.receive<PracticeOrderRequest>())) } }
        post("/orders/execute") { user(auth) { uid -> call.respond(service.execute(uid, call.receive<PracticeOrderRequest>())) } }
        post("/reset") { user(auth) { uid -> call.respond(service.reset(uid, call.receive<ResetRequest>())) } }
        get("/challenges") { user(auth) { uid -> call.respond(service.challenges(uid)) } }
        post("/challenges/{id}/complete") { user(auth) { uid -> call.respond(service.completeChallenge(uid, call.parameters["id"].orEmpty(), call.receive<ChallengeAnswer>())) } }
        // MOCK only: the route doesn't exist in REAL, and the service refuses it too.
        if (mock) put("/debug/scenario") { user(auth) { uid -> call.respond(service.loadScenario(uid, call.receive<PracticeScenarioRequest>().scenario)) } }
    }
}
