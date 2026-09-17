package cz.ctuprotebe.vyletnikviz;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Real network test from Android HttpURLConnection through the public internet to
 * the deployed AppDeploy API. It deliberately stops before AI generation:
 * invalid token => 401; valid token with empty plan => 400.
 */
@RunWith(AndroidJUnit4.class)
public class NativeBackendTransportTest {
    @Test public void deployedApiAcceptsNativeAndroidPostWithoutStartingAi() throws Exception {
        assertEquals(401, ServerQuizClient.probeDeployedPostStatus(false));
        assertEquals(400, ServerQuizClient.probeDeployedPostStatus(true));
    }
}
