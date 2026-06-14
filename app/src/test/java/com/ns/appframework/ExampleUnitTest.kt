package com.ns.appframework

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun xrayConfigGenerator_generatesValidJson() {
    val sampleNode = com.ns.appframework.data.V2RayParser.ParsedNode(
        protocol = "VMESS",
        remarks = "Test Germany Node",
        address = "104.16.24.5",
        port = 443,
        idOrPassword = "937bb1a1-cf0b-4bf4-a212-ff54388ffc99",
        streamType = "ws",
        path = "/testpath",
        security = "tls",
        sni = "tg.mci.ir",
        host = "tg.mci.ir"
    )

    val jsonConfig = com.ns.appframework.data.XrayConfigGenerator.generateJson(
        node = sampleNode,
        cleanIp = "172.64.36.42",
        customSni = "myfakesni.com"
    )

    // Verify vital parameters are contained in output JSON
    assertTrue(jsonConfig.contains("10808")) // SOCKS inbound port
    assertTrue(jsonConfig.contains("10809")) // HTTP inbound port
    assertTrue(jsonConfig.contains("vmess"))
    assertTrue(jsonConfig.contains("172.64.36.42")) // clean IP server address override
    assertTrue(jsonConfig.contains("myfakesni.com")) // custom SNI override
    assertTrue(jsonConfig.contains("/testpath")) // WebSocket path
  }

  @Test
  fun configAutomator_resolvesCorrectSniAndIp() {
    val cleanIpsList = listOf(
      com.ns.appframework.data.CleanIpEntity(ipAddress = "104.16.48.59", latencyMs = 70, provider = "Cloudflare", operatorName = "همراه اول (MCI)", status = "OPTIMAL"),
      com.ns.appframework.data.CleanIpEntity(ipAddress = "172.64.36.42", latencyMs = 90, provider = "Cloudflare", operatorName = "ایرانسل (Irancell)", status = "OPTIMAL")
    )

    // Test SNI mapping for MCI (Hamrah-e-Aval)
    val mciSni = com.ns.appframework.data.ConfigAutomator.getFakeSniForOperator("همراه اول (MCI)")
    assertEquals("tg.mci.ir", mciSni)

    // Test SNI mapping for Irancell
    val irancellSni = com.ns.appframework.data.ConfigAutomator.getFakeSniForOperator("ایرانسل (Irancell)")
    assertEquals("app.snapp.ir", irancellSni)

    // Test best IP resolution for MCI
    val resolvedMciIp = com.ns.appframework.data.ConfigAutomator.selectBestCleanIp(cleanIpsList, "همراه اول (MCI)")
    assertEquals("104.16.48.59", resolvedMciIp)

    // Test best IP resolution for Irancell
    val resolvedIrancellIp = com.ns.appframework.data.ConfigAutomator.selectBestCleanIp(cleanIpsList, "ایرانسل (Irancell)")
    assertEquals("172.64.36.42", resolvedIrancellIp)
  }
}
