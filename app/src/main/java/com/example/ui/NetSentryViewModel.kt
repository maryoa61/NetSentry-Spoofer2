package com.example.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NetSentryViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    // Sorting nodes state
    private val _sortNodesBy = MutableStateFlow("default") // "default", "ping", "speed", "rating"
    val sortNodesBy: StateFlow<String> = _sortNodesBy.asStateFlow()

    // Sorting clean IPs state
    private val _sortCleanIpsBy = MutableStateFlow("latency") // "latency", "provider", "ip"
    val sortCleanIpsBy: StateFlow<String> = _sortCleanIpsBy.asStateFlow()

    // Reactive streams from database
    val cleanIps: StateFlow<List<CleanIpEntity>> = repository.allCleanIps
        .combine(_sortCleanIpsBy) { list, sortBy ->
            when (sortBy) {
                "ip" -> list.sortedBy { it.ipAddress }
                "provider" -> list.sortedBy { it.provider }
                else -> list.sortedWith(compareBy { if (it.latencyMs == -1) Int.MAX_VALUE else it.latencyMs })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val v2rayNodes: StateFlow<List<V2RayNodeEntity>> = repository.allNodes
        .combine(_sortNodesBy) { list, sortBy ->
            when (sortBy) {
                "ping" -> list.sortedWith(compareBy { if (it.pingMs == -1) Int.MAX_VALUE else it.pingMs })
                "speed" -> list.sortedByDescending { it.downloadSpeedMbps }
                "rating" -> list.sortedByDescending { it.ratingStars }
                else -> list
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfiles: StateFlow<List<UserProfileEntity>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vpnSubscriptions: StateFlow<List<VpnSubscriptionEntity>> = repository.allSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State for Cleaner Scanner Page ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val _scannedOperator = MutableStateFlow("همراه اول (MCI)")
    val scannedOperator: StateFlow<String> = _scannedOperator.asStateFlow()

    private val _selectedProviders = MutableStateFlow(setOf("Cloudflare", "Akamai"))
    val selectedProviders: StateFlow<Set<String>> = _selectedProviders.asStateFlow()

    private val _scannerStatusText = MutableStateFlow("آماده جهت شروع اسکن")
    val scannerStatusText: StateFlow<String> = _scannerStatusText.asStateFlow()

    private val _scanPort = MutableStateFlow("443")
    val scanPort: StateFlow<String> = _scanPort.asStateFlow()

    private val _scanTimeoutMs = MutableStateFlow("1500")
    val scanTimeoutMs: StateFlow<String> = _scanTimeoutMs.asStateFlow()

    private val _scanConcurrency = MutableStateFlow("10")
    val scanConcurrency: StateFlow<String> = _scanConcurrency.asStateFlow()

    private val _scanLogs = MutableStateFlow<List<String>>(listOf("[SYSTEM] Shell loaded. Welcome to ip-scanner.sh console."))
    val scanLogs: StateFlow<List<String>> = _scanLogs.asStateFlow()

    // --- State for SNI Spoofer diagnostic Page ---
    private val _targetHost = MutableStateFlow("google.com")
    val targetHost: StateFlow<String> = _targetHost.asStateFlow()

    private val _fakeSni = MutableStateFlow("snapp.ir")
    val fakeSni: StateFlow<String> = _fakeSni.asStateFlow()

    private val _isSniTesting = MutableStateFlow(false)
    val isSniTesting: StateFlow<Boolean> = _isSniTesting.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    // --- State for V2Ray Nodes Configs ---
    private val _inputConfigUrl = MutableStateFlow("")
    val inputConfigUrl: StateFlow<String> = _inputConfigUrl.asStateFlow()

    private val _nodeError = MutableStateFlow<String?>(null)
    val nodeError: StateFlow<String?> = _nodeError.asStateFlow()

    private val _isTestingNodes = MutableStateFlow(false)
    val isTestingNodes: StateFlow<Boolean> = _isTestingNodes.asStateFlow()

    // --- State for Config Builder & Mobiles ---
    private val _selectedNodeForBuilder = MutableStateFlow<V2RayNodeEntity?>(null)
    val selectedNodeForBuilder: StateFlow<V2RayNodeEntity?> = _selectedNodeForBuilder.asStateFlow()

    private val _selectedCleanIpForBuilder = MutableStateFlow<String>("")
    val selectedCleanIpForBuilder: StateFlow<String> = _selectedCleanIpForBuilder.asStateFlow()

    private val _customSniForBuilder = MutableStateFlow("digikala.com")
    val customSniForBuilder: StateFlow<String> = _customSniForBuilder.asStateFlow()

    private val _builtConfigUrl = MutableStateFlow("")
    val builtConfigUrl: StateFlow<String> = _builtConfigUrl.asStateFlow()

    // --- State for live network monitoring speed simulation ---
    private val _liveDownloadSpeed = MutableStateFlow("0.0 KB/s")
    val liveDownloadSpeed: StateFlow<String> = _liveDownloadSpeed.asStateFlow()

    private val _liveUploadSpeed = MutableStateFlow("0.0 KB/s")
    val liveUploadSpeed: StateFlow<String> = _liveUploadSpeed.asStateFlow()

    private val _scannedDataMb = MutableStateFlow(0.0)
    val scannedDataMb: StateFlow<Double> = _scannedDataMb.asStateFlow()

    private var monitoringJob: Job? = null

    init {
        startNetworkMonitoringSimulation()
        loadDefaultSettings()
    }

    private fun loadDefaultSettings() {
        viewModelScope.launch {
            // Seed a few default V2Ray nodes to give the user a ready-to-test baseline!
            val mciVless = "vless://937bb1a1-cf0b-4bf4-a212-ff54388ffc99@104.16.24.5:443?type=ws&security=tls&path=%2Fnetsentry%3Fid%3D1&sni=tg.mci.ir#سرور_آلمان_همراه_اول"
            val irancellVmessMsg = "vmess://eyJhZGQiOiIxNzIuNjQuMzYuNDIiLCJhaWQiOiIwIiwiaG9zdCI6ImZpbHRlci5zbWFydC5uZXQiLCJpZCI6IjA0YmI0NmFmLTZjNDItNGE5Ni04ZjhhLTQ1MjkyNjg3NmI5YyIsIm5ldCI6IndzIiwicGF0aCI6Ii9zbWFydHdzIiwicG9ydCI6NDQzLCJwcyI6Itiz0LHYsdmIX9mH2YTZhtivX9mE2LHZhtmE2bwiLCJzY3kiOiJhdXRvIiwic25pIjoiYXBwLnNuYXBwLmlyIiwidGxzIjoidGxzIiwidiI6IjIifQ=="
            val trojanMci = "trojan://mypass123@104.17.39.81:443?security=tls&sni=tapsi.ir#سرور_فنلاند_ایرانسل"
            
            val parserList = listOf(mciVless, irancellVmessMsg, trojanMci)
            repository.allNodes.first().let { current ->
                if (current.isEmpty()) {
                    parserList.forEach { raw ->
                        val parsed = V2RayParser.parse(raw)
                        if (parsed != null) {
                            repository.insertNode(
                                V2RayNodeEntity(
                                    name = parsed.remarks,
                                    rawConfig = raw,
                                    protocol = parsed.protocol,
                                    serverHost = parsed.address,
                                    port = parsed.port,
                                    customSni = parsed.sni,
                                    assignedCleanIp = ""
                                )
                            )
                        }
                    }
                }
            }

            // Seed a few clean IPs if empty
            repository.allCleanIps.first().let { currentIps ->
                if (currentIps.isEmpty()) {
                    repository.insertCleanIp(CleanIpEntity(ipAddress = "104.16.24.5", latencyMs = 85, provider = "Cloudflare", operatorName = "همراه اول (MCI)", status = "OPTIMAL"))
                    repository.insertCleanIp(CleanIpEntity(ipAddress = "172.64.36.42", latencyMs = 110, provider = "Cloudflare", operatorName = "ایرانسل (Irancell)", status = "OPTIMAL"))
                    repository.insertCleanIp(CleanIpEntity(ipAddress = "23.212.100.41", latencyMs = 135, provider = "Akamai", operatorName = "همراه اول (MCI)", status = "CLEAN"))
                    repository.insertCleanIp(CleanIpEntity(ipAddress = "151.101.0.223", latencyMs = 210, provider = "Fastly", operatorName = "مخابرات (WiFi)", status = "CLEAN"))
                }
            }
        }
    }

    private fun startNetworkMonitoringSimulation() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (true) {
                delay(1200)
                if (_isScanning.value) {
                    val dSpeed = (12.0 + (0..18).random() + ((0..9).random() / 10.0))
                    val uSpeed = (1.1 + (0..3).random() + ((0..9).random() / 10.0))
                    _liveDownloadSpeed.value = String.format("%.1f MB/s", dSpeed)
                    _liveUploadSpeed.value = String.format("%.1f KB/s", uSpeed * 100)
                    _scannedDataMb.value += (dSpeed * 1.2)
                } else if (_isSniTesting.value || _isTestingNodes.value) {
                    val dSpeed = (1.2 + (0..4).random() + ((0..9).random() / 10.0))
                    val uSpeed = (0.5 + (0..1).random() + ((0..9).random() / 10.0))
                    _liveDownloadSpeed.value = String.format("%.1f MB/s", dSpeed)
                    _liveUploadSpeed.value = String.format("%.1f KB/s", uSpeed * 100)
                    _scannedDataMb.value += (dSpeed * 1.2)
                } else {
                    val dSpeed = ((0..150).random() / 10.0)
                    val uSpeed = ((0..40).random() / 10.0)
                    _liveDownloadSpeed.value = String.format("%.1f KB/s", dSpeed)
                    _liveUploadSpeed.value = String.format("%.1f KB/s", uSpeed)
                    _scannedDataMb.value += (dSpeed / 1024.0)
                }
            }
        }
    }

    // --- Scanning Controls ---
    fun toggleProviderSelection(provider: String) {
        val current = _selectedProviders.value.toMutableSet()
        if (current.contains(provider)) {
            if (current.size > 1) { // Keep at least one
                current.remove(provider)
            }
        } else {
            current.add(provider)
        }
        _selectedProviders.value = current
    }

    fun setScannedOperator(op: String) {
        _scannedOperator.value = op
    }

    fun updateScanPort(port: String) {
        _scanPort.value = port
    }

    fun updateScanTimeoutMs(timeout: String) {
        _scanTimeoutMs.value = timeout
    }

    fun updateScanConcurrency(concurrency: String) {
        _scanConcurrency.value = concurrency
    }

    fun clearScanLogs() {
        _scanLogs.value = listOf("[SYSTEM] Terminal buffer cleared.")
    }

    fun setSortCleanIpsBy(sortBy: String) {
        _sortCleanIpsBy.value = sortBy
    }

    fun startCdnScanner() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0
        _scannerStatusText.value = "شروع اتصال‌یابی به دامنه‌های مرجع CDN..."

        val port = _scanPort.value.toIntOrNull() ?: 443
        val timeout = _scanTimeoutMs.value.toIntOrNull() ?: 1500
        val concurrency = _scanConcurrency.value.toIntOrNull() ?: 10
        val operator = _scannedOperator.value
        val providersList = _selectedProviders.value.toList()

        val initialLogs = mutableListOf(
            "[SYSTEM] Executing custom high-speed CDN IP scanner script...",
            "[SYSTEM] Target network operator: $operator",
            "[SYSTEM] Selected CDN providers: ${providersList.joinToString(", ")}",
            "[SYSTEM] Parameters: port=$port, timeout=${timeout}ms, max_concurrency=$concurrency",
            "[INFO] Clearing old clean IPs from local database...",
            "----------------------------------------------------------------"
        )
        _scanLogs.value = initialLogs

        viewModelScope.launch {
            repository.clearCleanIps() // Clear previous results to reflect latest network state
            repository.scanCleanIps(
                providers = providersList,
                operatorName = operator,
                customPort = port,
                timeoutMs = timeout,
                concurrencyLimit = concurrency,
                onProgress = { progress, latestIp ->
                    _scanProgress.value = progress
                    _scannerStatusText.value = "آی‌پی سالم یافت شد: ${latestIp.ipAddress} (${latestIp.latencyMs}ms)"
                    
                    val formattedLog = if (latestIp.latencyMs != -1) {
                        "[SUCCESS] Probed IP: ${latestIp.ipAddress} | Provider: ${latestIp.provider} | RTT: ${latestIp.latencyMs}ms | Success: ${latestIp.successRate}%"
                    } else {
                        "[BLOCKED] Probed IP: ${latestIp.ipAddress} | Provider: ${latestIp.provider} | Timeout / Refused (0%)"
                    }
                    val currentLogs = _scanLogs.value.toMutableList()
                    currentLogs.add(formattedLog)
                    if (currentLogs.size > 150) {
                        currentLogs.removeAt(0)
                    }
                    _scanLogs.value = currentLogs
                }
            )
            val finalLogs = _scanLogs.value.toMutableList()
            finalLogs.add("----------------------------------------------------------------")
            finalLogs.add("[SYSTEM] Script execution completed successfully. Scanned clean IPs saved to database.")
            _scanLogs.value = finalLogs
            _isScanning.value = false
            _scannerStatusText.value = "اسکن با موفقیت به پایان رسید. آی‌پی‌های تمیز ذخیره شدند."
        }
    }

    fun clearScannedIps() {
        viewModelScope.launch {
            repository.clearCleanIps()
            _scanLogs.value = listOf("[SYSTEM] Clean IPs database cleared. Terminal console reset.")
        }
    }

    // --- SNI Spoofer Controls ---
    fun updateTargetHost(host: String) {
        _targetHost.value = host
    }

    fun updateFakeSni(sni: String) {
        _fakeSni.value = sni
    }

    fun startSniDiagnostic() {
        if (_isSniTesting.value) return
        _isSniTesting.value = true
        _diagnosticLogs.value = emptyList()

        viewModelScope.launch {
            val success = repository.runSniSpoofDiagnostic(
                targetHost = _targetHost.value,
                fakeSni = _fakeSni.value,
                onLog = { log ->
                    _diagnosticLogs.value = _diagnosticLogs.value + log
                }
            )
            _isSniTesting.value = false
        }
    }

    // --- V2Ray Nodes Controls ---
    fun updateInputConfigUrl(url: String) {
        _inputConfigUrl.value = url
        _nodeError.value = null
    }

    fun addNewNode() {
        val url = _inputConfigUrl.value.trim()
        if (url.isBlank()) {
            _nodeError.value = "لطفاً لینک کانفیگ را وارد کنید."
            return
        }

        val parsed = V2RayParser.parse(url)
        if (parsed == null) {
            _nodeError.value = "ساختار لینک V2Ray نامعتبر است. از پروتکل‌های vmess, vless, trojan یا ss پشتیبانی می‌شود."
            return
        }

        viewModelScope.launch {
            repository.insertNode(
                V2RayNodeEntity(
                    name = parsed.remarks,
                    rawConfig = url,
                    protocol = parsed.protocol,
                    serverHost = parsed.address,
                    port = parsed.port,
                    customSni = parsed.sni,
                    assignedCleanIp = ""
                )
            )
            _inputConfigUrl.value = ""
            _nodeError.value = null
        }
    }

    fun testAllNodes() {
        if (_isTestingNodes.value) return
        _isTestingNodes.value = true

        viewModelScope.launch {
            val list = v2rayNodes.value
            list.forEach { node ->
                repository.testNodePing(node)
                delay(200)
            }
            _isTestingNodes.value = false
        }
    }

    fun deleteNode(node: V2RayNodeEntity) {
        viewModelScope.launch {
            repository.deleteNode(node)
            if (_selectedNodeForBuilder.value?.id == node.id) {
                _selectedNodeForBuilder.value = null
                _builtConfigUrl.value = ""
            }
        }
    }

    // --- Config Builder Controls ---
    fun selectNodeForBuilder(node: V2RayNodeEntity) {
        _selectedNodeForBuilder.value = node
        updateBuiltConfig()
    }

    fun selectCleanIpForBuilder(ip: String) {
        _selectedCleanIpForBuilder.value = ip
        updateBuiltConfig()
    }

    fun updateCustomSniForBuilder(sni: String) {
        _customSniForBuilder.value = sni
        updateBuiltConfig()
    }

    fun updateBuiltConfig() {
        val nodeEntity = _selectedNodeForBuilder.value ?: return
        val cleanIp = _selectedCleanIpForBuilder.value
        val fakeSni = _customSniForBuilder.value

        val parsed = V2RayParser.parse(nodeEntity.rawConfig)
        if (parsed != null) {
            _builtConfigUrl.value = V2RayParser.buildConfig(
                node = parsed,
                cleanIp = cleanIp,
                customSni = fakeSni
            )
        }
    }

    // --- User Profiles Form States & Methods ---
    private val _profileNameInput = MutableStateFlow("")
    val profileNameInput: StateFlow<String> = _profileNameInput.asStateFlow()

    fun updateProfileNameInput(name: String) {
        _profileNameInput.value = name
    }

    fun saveCurrentAsProfile() {
        val name = _profileNameInput.value.trim()
        if (name.isEmpty()) return
        
        viewModelScope.launch {
            repository.deactivateAllProfiles()
            
            val providersStr = _selectedProviders.value.joinToString(",")
            val profile = UserProfileEntity(
                profileName = name,
                preferredOperator = _scannedOperator.value,
                selectedProvidersJson = providersStr,
                targetHost = _targetHost.value,
                fakeSni = _fakeSni.value,
                customSniForBuilder = _customSniForBuilder.value,
                isAutoTestEnabled = _isAutoTesting.value,
                isActive = true
            )
            repository.insertProfile(profile)
            _profileNameInput.value = ""
        }
    }

    fun loadProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.deactivateAllProfiles()
            repository.updateProfile(profile.copy(isActive = true))
            
            _scannedOperator.value = profile.preferredOperator
            val providers = profile.selectedProvidersJson.split(",").filter { it.isNotBlank() }.toSet()
            if (providers.isNotEmpty()) {
                _selectedProviders.value = providers
            }
            _targetHost.value = profile.targetHost
            _fakeSni.value = profile.fakeSni
            _customSniForBuilder.value = profile.customSniForBuilder
            
            _isAutoTesting.value = profile.isAutoTestEnabled
            if (profile.isAutoTestEnabled) {
                startAutoTestingNodes()
            } else {
                stopAutoTestingNodes()
            }
            
            updateBuiltConfig()
        }
    }

    fun deleteProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
        }
    }

    // --- Subscription Form States & Methods ---
    private val _subNameInput = MutableStateFlow("")
    val subNameInput: StateFlow<String> = _subNameInput.asStateFlow()

    private val _subUrlInput = MutableStateFlow("")
    val subUrlInput: StateFlow<String> = _subUrlInput.asStateFlow()

    private val _subApiKeyInput = MutableStateFlow("")
    val subApiKeyInput: StateFlow<String> = _subApiKeyInput.asStateFlow()

    private val _isSyncingSub = MutableStateFlow(false)
    val isSyncingSub: StateFlow<Boolean> = _isSyncingSub.asStateFlow()

    fun updateSubNameInput(name: String) { _subNameInput.value = name }
    fun updateSubUrlInput(url: String) { _subUrlInput.value = url }
    fun updateSubApiKeyInput(key: String) { _subApiKeyInput.value = key }

    fun addAndSyncSubscription() {
        val name = _subNameInput.value.trim()
        val url = _subUrlInput.value.trim()
        val apiKey = _subApiKeyInput.value.trim()

        if (name.isEmpty() || url.isEmpty()) return

        _isSyncingSub.value = true
        viewModelScope.launch {
            val sub = VpnSubscriptionEntity(
                providerName = name,
                subscriptionUrl = url,
                apiKey = apiKey
            )
            repository.insertSubscription(sub)
            repository.syncSubscriptionNodes(sub)
            
            _subNameInput.value = ""
            _subUrlInput.value = ""
            _subApiKeyInput.value = ""
            _isSyncingSub.value = false
        }
    }

    fun syncSubscription(sub: VpnSubscriptionEntity) {
        _isSyncingSub.value = true
        viewModelScope.launch {
            repository.syncSubscriptionNodes(sub)
            _isSyncingSub.value = false
        }
    }

    fun deleteSubscription(sub: VpnSubscriptionEntity) {
        viewModelScope.launch {
            repository.deleteSubscription(sub)
        }
    }

    // --- BPB & Edge Tunnel Form States & Methods ---
    private val _bpbPanelType = MutableStateFlow("BPB") // "BPB", "EDGE"
    val bpbPanelType: StateFlow<String> = _bpbPanelType.asStateFlow()

    private val _bpbDomainInput = MutableStateFlow("")
    val bpbDomainInput: StateFlow<String> = _bpbDomainInput.asStateFlow()

    private val _bpbUuidInput = MutableStateFlow("51109985-c05e-4c74-bef1-1bfa443209ca")
    val bpbUuidInput: StateFlow<String> = _bpbUuidInput.asStateFlow()

    private val _bpbPathInput = MutableStateFlow("/")
    val bpbPathInput: StateFlow<String> = _bpbPathInput.asStateFlow()

    private val _bpbPortInput = MutableStateFlow("443")
    val bpbPortInput: StateFlow<String> = _bpbPortInput.asStateFlow()

    private val _bpbSelectedCleanIp = MutableStateFlow("104.16.24.5")
    val bpbSelectedCleanIp: StateFlow<String> = _bpbSelectedCleanIp.asStateFlow()

    fun updateBpbPanelType(type: String) {
        _bpbPanelType.value = type
        if (type == "BPB" && (_bpbPathInput.value == "/" || _bpbPathInput.value == "/?edgetunnel=1")) {
            _bpbPathInput.value = "/?bpb=1"
        } else if (type == "EDGE" && (_bpbPathInput.value == "/" || _bpbPathInput.value == "/?bpb=1")) {
            _bpbPathInput.value = "/?edgetunnel=1"
        }
    }

    fun updateBpbDomainInput(domain: String) {
        _bpbDomainInput.value = domain
    }

    fun updateBpbUuidInput(uuid: String) {
        _bpbUuidInput.value = uuid
    }

    fun generateRandomBpbUuid() {
        _bpbUuidInput.value = java.util.UUID.randomUUID().toString()
    }

    fun updateBpbPathInput(path: String) {
        _bpbPathInput.value = path
    }

    fun updateBpbPortInput(port: String) {
        _bpbPortInput.value = port
    }

    fun updateBpbSelectedCleanIp(ip: String) {
        _bpbSelectedCleanIp.value = ip
    }

    fun generateAndSaveBpbNode() {
        val domain = _bpbDomainInput.value.trim().lowercase()
        if (domain.isEmpty()) return

        val type = _bpbPanelType.value
        val uuid = _bpbUuidInput.value.trim()
        val path = _bpbPathInput.value.trim()
        val portStr = _bpbPortInput.value.trim()
        val port = portStr.toIntOrNull() ?: 443
        val cleanIp = _bpbSelectedCleanIp.value.trim().ifEmpty { "104.16.24.5" }

        val remarks = if (type == "BPB") "BPB | $domain" else "EdgeTunnel | $domain"
        
        val encodedPath = try {
            java.net.URLEncoder.encode(path, "UTF-8")
        } catch (e: Exception) {
            path
        }
        val encodedSni = try {
            java.net.URLEncoder.encode(domain, "UTF-8")
        } catch (e: Exception) {
            domain
        }
        val encodedRemarks = try {
            java.net.URLEncoder.encode(remarks, "UTF-8")
        } catch (e: Exception) {
            remarks
        }

        val rawConfigUrl = "vless://$uuid@$cleanIp:$port?type=ws&security=tls&path=$encodedPath&sni=$encodedSni&host=$encodedSni#$encodedRemarks"

        viewModelScope.launch {
            repository.insertNode(
                V2RayNodeEntity(
                    name = remarks,
                    rawConfig = rawConfigUrl,
                    protocol = "VLESS",
                    serverHost = cleanIp,
                    port = port,
                    customSni = domain,
                    assignedCleanIp = cleanIp
                )
            )
            _bpbDomainInput.value = ""
        }
    }

    // --- Node Sorting Control ---
    fun setSortNodesBy(sort: String) {
        _sortNodesBy.value = sort
    }

    // --- Periodic Automated Network Testing Timer ---
    private val _isAutoTesting = MutableStateFlow(false)
    val isAutoTesting: StateFlow<Boolean> = _isAutoTesting.asStateFlow()
    private var autoTestJob: Job? = null

    fun toggleAutoNodeTesting() {
        _isAutoTesting.value = !_isAutoTesting.value
        if (_isAutoTesting.value) {
            startAutoTestingNodes()
        } else {
            stopAutoTestingNodes()
        }
    }

    private fun startAutoTestingNodes() {
        autoTestJob?.cancel()
        autoTestJob = viewModelScope.launch {
            while (_isAutoTesting.value) {
                testAllNodes()
                delay(20000) // 20 seconds repeat test
            }
        }
    }

    private fun stopAutoTestingNodes() {
        autoTestJob?.cancel()
        autoTestJob = null
    }

    override fun onCleared() {
        super.onCleared()
        monitoringJob?.cancel()
        autoTestJob?.cancel()
    }
}

class NetSentryViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NetSentryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NetSentryViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
