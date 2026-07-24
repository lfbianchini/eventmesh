import http from "k6/http";
import { check } from "k6";

const baseUrl = __ENV.BASE_URL || "http://host.docker.internal:8088";
const apiKey = __ENV.API_KEY || "em_dev_local_key_change_me";
const runId = __ENV.RUN_ID || `${Date.now()}`;

export const options = {
  scenarios: {
    ingress: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "20s",
      gracefulStop: "5s",
    },
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

export default function () {
  const response = http.post(
    `${baseUrl}/v1/events`,
    JSON.stringify({
      type: "benchmark.ingress",
      source: "k6.local",
      subject: `vu-${__VU}`,
      data: { iteration: __ITER },
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-EventMesh-Key": apiKey,
        "Idempotency-Key": `k6-${runId}-${__VU}-${__ITER}`,
      },
    },
  );

  check(response, {
    "event accepted": (result) => result.status === 201,
  });
}
