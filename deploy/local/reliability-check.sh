#!/usr/bin/env bash
# Phase 5B Local reliability/security checks. Touches only stocksteps-local-api-1 and throwaway stocksteps-local-hc-* containers.
set -u
C=stocksteps-local-api-1
IMG=$(docker inspect -f '{{.Config.Image}}' $C)
echo "== after load: memory/cpu"; docker stats --no-stream --format 'mem={{.MemUsage}} cpu={{.CPUPerc}} pids={{.PIDs}}' $C
echo "cgroup peak: $(docker exec $C cat /sys/fs/cgroup/memory.peak 2>/dev/null | awk '{printf "%.0f MiB", $1/1048576}') limit: $(docker exec $C cat /sys/fs/cgroup/memory.max | awk '{printf "%.0f MiB", $1/1048576}')"
echo "== security config"
docker inspect -f 'user={{.Config.User}} readonly={{.HostConfig.ReadonlyRootfs}} privileged={{.HostConfig.Privileged}} capdrop={{.HostConfig.CapDrop}} capadd={{.HostConfig.CapAdd}} secopt={{.HostConfig.SecurityOpt}} pids={{.HostConfig.PidsLimit}} memory={{.HostConfig.Memory}} nanocpus={{.HostConfig.NanoCpus}} restart={{.HostConfig.RestartPolicy.Name}} log={{.HostConfig.LogConfig.Type}}{{.HostConfig.LogConfig.Config}} network={{.HostConfig.NetworkMode}} tmpfs={{.HostConfig.Tmpfs}}' $C
echo "ports: $(docker port $C)"; echo "host listeners on 8081: $(ss -Hltn | awk '{print $4}' | grep ':8081$' | tr '\n' ' ')"
echo "PID1 uid: $(docker exec $C awk '/^Uid/{print $2}' /proc/1/status)  root write test: $(docker exec $C sh -c 'touch /app/x 2>&1 >/dev/null && echo WRITABLE || echo denied'; docker exec $C sh -c 'touch /etc/x 2>/dev/null && echo etc-WRITABLE || echo etc-denied')"
echo "env keys: $(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $C | cut -d= -f1 | tr '\n' ' ')"
echo "secret-like env values: $(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $C | grep -c -i -E 'API_KEY|TOKEN|SECRET|PASSWORD|CREDENTIALS')"
echo "== logs"; LP=$(docker inspect -f '{{.LogPath}}' $C); echo "log lines: $(docker logs $C 2>&1 | wc -l)  secret-like lines: $(docker logs $C 2>&1 | grep -c -i -E 'apikey=|api_key=|bearer [a-z0-9]|mock-user:')"
docker logs --tail 3 $C 2>&1 | cut -c1-160
echo "== in-memory state + crash restart (SIGTERM to PID 1 from inside = process exit, not a docker stop)"
before=$(docker inspect -f '{{.RestartCount}} {{.State.StartedAt}}' $C)
docker exec $C bash -c 'kill -TERM 1'
for i in $(seq 1 90); do s=$(docker inspect -f '{{.State.Health.Status}} {{.RestartCount}}' $C 2>/dev/null); [[ "$s" == healthy* && "${s#* }" != "${before%% *}" ]] && break; sleep 2; done
echo "before: restarts/started=$before  after: $(docker inspect -f '{{.RestartCount}} {{.State.StartedAt}} health={{.State.Health.Status}} exit={{.State.ExitCode}}' $C) (waited ~$((i*2))s)"
echo "== health check detects an unresponsive backend (throwaway containers, --network none)"
HC=(--health-cmd 'curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:8080/health/ready' --health-interval 2s --health-timeout 3s --health-retries 2 --health-start-period 3s)
common=(--network none --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges:true --memory 1g --cpus 1 -e STOCKSTEPS_DATA_MODE=mock -e LOG_FORMAT=text)
docker rm -f stocksteps-local-hc-ok stocksteps-local-hc-bad >/dev/null 2>&1
docker run -d --name stocksteps-local-hc-ok "${common[@]}" "${HC[@]}" "$IMG" >/dev/null
docker run -d --name stocksteps-local-hc-bad "${common[@]}" "${HC[@]}" -e PORT=9090 "$IMG" >/dev/null   # app listens on 9090, probe hits 8080
sleep 25
echo "control (app on 8080): $(docker inspect -f '{{.State.Health.Status}} failing={{.State.Health.FailingStreak}}' stocksteps-local-hc-ok)"
echo "backend not answering on the probed port: $(docker inspect -f '{{.State.Health.Status}} failing={{.State.Health.FailingStreak}}' stocksteps-local-hc-bad)"
docker rm -f stocksteps-local-hc-ok stocksteps-local-hc-bad >/dev/null
echo "== other services"
docker ps --format '{{.Names}} {{.Status}}' | grep -v stocksteps
echo "leftover hc containers: $(docker ps -a --filter name=stocksteps-local-hc --format '{{.Names}}' | wc -l)"
