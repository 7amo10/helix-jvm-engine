package com.helix.core.reorder;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class PolicyProberTest {

    @Test
    void probePolicy() throws Exception {
        URL url = getClass().getResource("/models/ast_reorder_policy.onnx");
        File file = new File(url.getFile());
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession session = env.createSession(file.getAbsolutePath(), new OrtSession.SessionOptions())) {
            // Test 1: All zeros
            float[][] input = new float[1][82];
            runAndPrint("All Zeros", session, env, input);

            // Test 3: Node 1 features (indices 4..7)
            float[][] inpNode1Cost = new float[1][82];
            inpNode1Cost[0][4] = 1.0f; // node 1, feat 0
            runAndPrint("Node 1 feat 0 (cost) = 1.0", session, env, inpNode1Cost);

            float[][] inpNode1Fail = new float[1][82];
            inpNode1Fail[0][5] = 1.0f; // node 1, feat 1
            runAndPrint("Node 1 feat 1 (fail) = 1.0", session, env, inpNode1Fail);

            // Test 4: Scenario with 2 nodes:
            // Node 0: expensive ML (high cost = 1.0, fail = 0.05, ratio = 20.0)
            // Node 1: cheap check (low cost = 0.01, fail = 0.95, ratio = 0.0105)
            // Global: 2 candidate nodes
            float[][] scenario = new float[1][82];
            scenario[0][0] = 1.0f;  // node 0 cost
            scenario[0][1] = 0.05f; // node 0 fail
            scenario[0][2] = 1.0f;  // node 0 count
            scenario[0][3] = 1.0f;  // node 0 ratio
            scenario[0][4] = 0.01f; // node 1 cost
            scenario[0][5] = 0.95f; // node 1 fail
            scenario[0][6] = 1.0f;  // node 1 count
            scenario[0][7] = 0.01f; // node 1 ratio
            scenario[0][80] = 2.0f; // global node count
            scenario[0][81] = 0.0f; // step 0
            runAndPrint("2-Node Scenario (ML vs Cheap)", session, env, scenario);
        }
    }

    private void runAndPrint(String label, OrtSession session, OrtEnvironment env, float[][] input) throws Exception {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input);
             OrtSession.Result result = session.run(Collections.singletonMap("observation", tensor))) {
            float[][] logits = (float[][]) result.get(0).getValue();
            System.out.printf("[%s] logits[0..3]: [%.4f, %.4f, %.4f, %.4f]%n",
                    label, logits[0][0], logits[0][1], logits[0][2], logits[0][3]);
        }
    }
}
