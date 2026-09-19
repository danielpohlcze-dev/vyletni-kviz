package cz.ctuprotebe.vyletnikviz;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Real network test from Android HttpURLConnection through the public internet to
 * the deployed AppDeploy API. It deliberately stops before AI generation:
 * invalid token => 401; valid token with empty plan => 400.
 * If AppDeploy itself is out of credits, its gateway can return 402 before our
 * route executes; that is a recognized failover condition rather than an
 * Android transport failure.
 */
@RunWith(AndroidJUnit4.class)
public class NativeBackendTransportTest {
    @Test public void deployedApiAcceptsNativeAndroidPostWithoutStartingAi() throws Exception {
        int denied = ServerQuizClient.probeDeployedPostStatus(false);
        assertTrue(denied == 401 || denied == 402);
        if (denied == 402) return;
        int valid = ServerQuizClient.probeDeployedPostStatus(true);
        assertTrue(valid == 400 || valid == 402);
    }
}
