package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.model.DraftDto
import com.example.data.model.PartyDto
import com.example.data.repository.AuthState
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.MfaChallengeScreen
import com.example.ui.screens.auth.MfaEnrollScreen
import com.example.ui.screens.corpus.LegalCorpusScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.deadlines.DeadlinesListScreen
import com.example.ui.screens.matters.MatterDetailScreen
import com.example.ui.screens.matters.MattersListScreen
import com.example.ui.screens.research.ResearchScreen
import com.example.ui.screens.review.ReviewQueueScreen
import com.example.ui.screens.roll.HearingRollScreen
import com.example.ui.screens.scanner.DocumentScannerScreen
import com.example.ui.screens.search.ArchiveSearchScreen
import com.example.ui.screens.templates.TemplatesScreen
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.AppThemeSetting
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.HoyaamTheme
import com.example.ui.theme.LocalHoyaamColors
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "الرئيسية", Icons.Outlined.Dashboard)
    object Matters : Screen("matters", "القضايا", Icons.Outlined.Folder)
    object Roll : Screen("roll", "الرول", Icons.Outlined.CalendarMonth)
    object Deadlines : Screen("deadlines", "المواعيد", Icons.Outlined.Alarm)
    object Review : Screen("review", "المراجعة", Icons.Outlined.FactCheck)
    object Research : Screen("research", "بحث قانوني", Icons.Outlined.Gavel)
    object Archive : Screen("archive", "الأرشيف", Icons.Outlined.Search)
    object Corpus : Screen("corpus", "المصادر", Icons.Outlined.MenuBook)
    object Templates : Screen("templates", "القوالب", Icons.Outlined.Assignment)
}

val BOTTOM_NAV_ITEMS = listOf(
    Screen.Dashboard,
    Screen.Matters,
    Screen.Roll,
    Screen.Deadlines,
    Screen.Review
)

val MORE_NAV_ITEMS = listOf(
    Screen.Research,
    Screen.Archive,
    Screen.Corpus,
    Screen.Templates
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeSetting by viewModel.themeSetting.collectAsStateWithLifecycle()
            val isDynamicColor by viewModel.isDynamicColor.collectAsStateWithLifecycle()
            val langCode by viewModel.langCode.collectAsStateWithLifecycle()
            val layoutDirection = if (langCode == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                HoyaamTheme(
                    themeSetting = themeSetting,
                    dynamicColor = isDynamicColor
                ) {
                    val authState by viewModel.authState.collectAsStateWithLifecycle()

                    when (val state = authState) {
                        is AuthState.Unauthenticated -> {
                            var isLoading by remember { mutableStateOf(false) }
                            var errorMessage by remember { mutableStateOf<String?>(null) }
                            val scope = rememberCoroutineScope()

                            LoginScreen(
                                onSignIn = { email, pass ->
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val result = viewModel.repository.signInWithPassword(email, pass)
                                        isLoading = false
                                        result.onFailure {
                                            errorMessage = it.message ?: "فشل تسجيل الدخول. تحقق من البريد أو كلمة المرور."
                                        }
                                    }
                                },
                                isLoading = isLoading,
                                errorMessage = errorMessage
                            )
                        }

                        is AuthState.RequiresMfaEnroll -> {
                            var isLoading by remember { mutableStateOf(false) }
                            var errorMessage by remember { mutableStateOf<String?>(null) }
                            val scope = rememberCoroutineScope()

                            MfaEnrollScreen(
                                factor = state.factor,
                                onVerifyCode = { code ->
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val result = viewModel.repository.challengeAndVerifyMfa(state.factor.id, code)
                                        isLoading = false
                                        result.onFailure {
                                            errorMessage = it.message ?: "رمز التحقق غير صحيح. حاول مجدداً."
                                        }
                                    }
                                },
                                isLoading = isLoading,
                                errorMessage = errorMessage
                            )
                        }

                        is AuthState.RequiresMfaChallenge -> {
                            var isLoading by remember { mutableStateOf(false) }
                            var errorMessage by remember { mutableStateOf<String?>(null) }
                            val scope = rememberCoroutineScope()

                            MfaChallengeScreen(
                                onVerifyCode = { code ->
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val result = viewModel.repository.verifyChallenge(state.factorId, state.challengeId, code)
                                        isLoading = false
                                        result.onFailure {
                                            errorMessage = it.message ?: "رمز التحقق غير صحيح."
                                        }
                                    }
                                },
                                isLoading = isLoading,
                                errorMessage = errorMessage
                            )
                        }

                        is AuthState.Authenticated -> {
                            MainAppContent(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val colors = LocalHoyaamColors.current
    val context = LocalContext.current
    val navController = rememberNavController()

    val themeSetting by viewModel.themeSetting.collectAsStateWithLifecycle()
    val isDynamicColor by viewModel.isDynamicColor.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val langCode by viewModel.langCode.collectAsStateWithLifecycle()
    val urgentAlerts by viewModel.urgentAlerts.collectAsStateWithLifecycle()
    val matters by viewModel.matters.collectAsStateWithLifecycle()
    val authorities by viewModel.authorities.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val pendingExtractions by viewModel.pendingExtractions.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    // Backs the pull-to-refresh gesture on every tab below.
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val currentNavBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentNavBackStackEntry?.destination?.route

    // Every mutation in the ViewModel used to fail silently — a dialog
    // would just close whether the underlying write succeeded or not.
    // This surfaces the real error message from the backend (or a
    // fallback) as a Snackbar, the same way the auth flow already did
    // correctly.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    // sign-document-url and litigation-export-draft both return a
    // short-lived signed URL — opening it is a normal web/download link,
    // so handing it to the system via ACTION_VIEW (browser or whatever
    // app is registered for it) is the correct way to open it; no
    // in-app viewer needed for a link that expires in minutes anyway.
    val openedDocumentUrl by viewModel.openedDocumentUrl.collectAsStateWithLifecycle()
    LaunchedEffect(openedDocumentUrl) {
        openedDocumentUrl?.let { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("تعذّر فتح المستند: ${e.message ?: "لا يوجد تطبيق لفتح هذا الرابط"}")
            }
            viewModel.clearOpenedDocumentUrl()
        }
    }
    val exportedDraftUrl by viewModel.exportedDraftUrl.collectAsStateWithLifecycle()
    LaunchedEffect(exportedDraftUrl) {
        exportedDraftUrl?.let { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("تعذّر فتح ملف Word: ${e.message ?: "لا يوجد تطبيق لفتح هذا الرابط"}")
            }
            viewModel.clearExportedDraftUrl()
        }
    }

    // Dialog state holders
    var showNewMatterDialog by remember { mutableStateOf(false) }
    var showEditMatterDialog by remember { mutableStateOf(false) }
    var showPartyDialog by remember { mutableStateOf(false) }
    var editingParty by remember { mutableStateOf<PartyDto?>(null) }
    var showRecordHearingDialog by remember { mutableStateOf(false) }
    var showProposeDeadlineDialog by remember { mutableStateOf(false) }
    var showUploadDocsDialog by remember { mutableStateOf(false) }
    var showNewDraftDialog by remember { mutableStateOf(false) }
    var isNewDraftMemo by remember { mutableStateOf(false) }
    var showFillTemplateDialog by remember { mutableStateOf(false) }
    var showCitationInspectorDialog by remember { mutableStateOf(false) }
    var inspectingDraft by remember { mutableStateOf<DraftDto?>(null) }
    var showAddAuthorityDialog by remember { mutableStateOf(false) }
    var showAddTemplateDialog by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }

    val topBarTitle = when {
        currentRoute == Screen.Dashboard.route -> "لوحة المتابعة الإجرائية"
        currentRoute == Screen.Matters.route -> "ملفات القضايا"
        currentRoute?.startsWith("matter_detail") == true -> "تفاصيل القضية"
        currentRoute == Screen.Roll.route -> "رول الجلسات والأجندة"
        currentRoute == Screen.Deadlines.route -> "المواعيد الحتمية"
        currentRoute == Screen.Review.route -> "مراجعة الاستخراج الذكي"
        currentRoute == Screen.Research.route -> "بحث قانوني موثّق"
        currentRoute == Screen.Archive.route -> "أرشيف المكتب"
        currentRoute == Screen.Corpus.route -> "المصادر القانونية"
        currentRoute == Screen.Templates.route -> "قوالب الصياغة"
        else -> "هويام المحامية"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = topBarTitle,
                subtitle = "هويام AI — جمهورية مصر العربية",
                urgentAlerts = urgentAlerts,
                isDarkTheme = isDarkTheme,
                onToggleTheme = { viewModel.toggleTheme() },
                currentLang = langCode,
                onToggleLang = { viewModel.toggleLang() },
                onSignOut = { viewModel.repository.signOut() },
                onAlertClick = { alert ->
                    if (alert.matterId.isNotBlank()) {
                        viewModel.loadMatter(alert.matterId)
                        navController.navigate("matter_detail/${alert.matterId}")
                    }
                },
                onConfirmAlertDeadline = { deadlineId ->
                    viewModel.confirmDeadline(deadlineId)
                }
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = colors.border.copy(alpha = 0.7f), thickness = 1.dp)
                NavigationBar(
                    containerColor = colors.bg,
                    contentColor = colors.text,
                    tonalElevation = 0.dp
                ) {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.title,
                                    tint = if (selected) colors.heroText else colors.textDim
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    color = if (selected) colors.text else colors.textDim,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontFamily = ArabicSansFontFamily
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = colors.heroBg,
                                selectedIconColor = colors.heroText,
                                unselectedIconColor = colors.textDim,
                                selectedTextColor = colors.text,
                                unselectedTextColor = colors.textDim
                            )
                        )
                    }

                    // "المزيد" Item for Drawer/More options
                    val isMoreSelected = MORE_NAV_ITEMS.any { it.route == currentRoute }
                    NavigationBarItem(
                        selected = isMoreSelected,
                        onClick = { showMoreMenuSheet = true },
                        icon = {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "المزيد",
                                tint = if (isMoreSelected) colors.heroText else colors.textDim
                            )
                        },
                        label = {
                            Text(
                                text = "المزيد",
                                color = if (isMoreSelected) colors.text else colors.textDim,
                                fontSize = 11.sp,
                                fontWeight = if (isMoreSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontFamily = ArabicSansFontFamily
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = colors.heroBg,
                            selectedIconColor = colors.heroText,
                            unselectedIconColor = colors.textDim,
                            selectedTextColor = colors.text,
                            unselectedTextColor = colors.textDim
                        )
                    )
                }
            }
        },
        containerColor = colors.bg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
            ) {
                composable(Screen.Dashboard.route) {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        DashboardScreen(
                            stats = stats,
                            isLoading = false,
                            onNavigateToRoll = { navController.navigate(Screen.Roll.route) },
                            onNavigateToDeadlines = { navController.navigate(Screen.Deadlines.route) },
                            onNavigateToReview = { navController.navigate(Screen.Review.route) },
                            onNavigateToTemplates = { navController.navigate(Screen.Templates.route) },
                            onNavigateToMatter = { matterId ->
                                viewModel.loadMatter(matterId)
                                navController.navigate("matter_detail/$matterId")
                            },
                            onConfirmDeadline = { deadlineId ->
                                viewModel.confirmDeadline(deadlineId)
                            }
                        )
                    }
                }

                composable(Screen.Matters.route) {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        MattersListScreen(
                            matters = matters,
                            isLoading = false,
                            onMatterClick = { matterId ->
                                viewModel.loadMatter(matterId)
                                navController.navigate("matter_detail/$matterId")
                            },
                            onOpenNewMatterDialog = { showNewMatterDialog = true }
                        )
                    }
                }

                composable(
                    route = "matter_detail/{matterId}",
                    arguments = listOf(navArgument("matterId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val matterId = backStackEntry.arguments?.getString("matterId") ?: ""
                    val selectedMatter by viewModel.selectedMatter.collectAsStateWithLifecycle()
                    val parties by viewModel.matterParties.collectAsStateWithLifecycle()
                    val hearings by viewModel.matterHearings.collectAsStateWithLifecycle()
                    val deadlines by viewModel.matterDeadlines.collectAsStateWithLifecycle()
                    val documents by viewModel.matterDocuments.collectAsStateWithLifecycle()
                    val drafts by viewModel.matterDrafts.collectAsStateWithLifecycle()
                    val cachedAnalysis by viewModel.cachedCaseAnalysis.collectAsStateWithLifecycle()
                    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
                    val isGeneratingChronology by viewModel.isGeneratingChronology.collectAsStateWithLifecycle()
                    val chronologyResponse by viewModel.chronologyResponse.collectAsStateWithLifecycle()
                    val localSummary by viewModel.localCaseSummary.collectAsStateWithLifecycle()
                    val localNotes by viewModel.localCaseNotes.collectAsStateWithLifecycle()
                    val localScans by viewModel.localScannedDocs.collectAsStateWithLifecycle()
                    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()
                    val summaryResult by viewModel.summaryResult.collectAsStateWithLifecycle()
                    val isGeneratingBriefing by viewModel.isGeneratingBriefing.collectAsStateWithLifecycle()
                    val isCheckingConflicts by viewModel.isCheckingConflicts.collectAsStateWithLifecycle()
                    val conflictCheckResult by viewModel.conflictCheckResult.collectAsStateWithLifecycle()

                    LaunchedEffect(matterId) {
                        viewModel.loadMatter(matterId)
                    }

                    if (selectedMatter != null) {
                        MatterDetailScreen(
                            matter = selectedMatter!!,
                            parties = parties,
                            hearings = hearings,
                            deadlines = deadlines,
                            documents = documents,
                            drafts = drafts,
                            cachedAnalysis = cachedAnalysis,
                            isAnalyzing = isAnalyzing,
                            isGeneratingChronology = isGeneratingChronology,
                            chronologyResponse = chronologyResponse,
                            localSummary = localSummary,
                            localNotes = localNotes,
                            localScans = localScans,
                            onBackClick = { navController.popBackStack() },
                            onEditMatterClick = { showEditMatterDialog = true },
                            onUploadDocsClick = { showUploadDocsDialog = true },
                            onAnalyzeCaseClick = { viewModel.analyzeCase() },
                            onAddPartyClick = {
                                editingParty = null
                                showPartyDialog = true
                            },
                            onEditPartyClick = { party ->
                                editingParty = party
                                showPartyDialog = true
                            },
                            onDeletePartyClick = { partyId -> viewModel.deleteParty(partyId) },
                            onRecordHearingClick = { showRecordHearingDialog = true },
                            onProposeDeadlineClick = { showProposeDeadlineDialog = true },
                            onConfirmDeadlineClick = { deadlineId -> viewModel.confirmDeadline(deadlineId) },
                            onOpenDocument = { doc -> viewModel.openDocument(doc) },
                            onNewDraftClick = {
                                isNewDraftMemo = false
                                showNewDraftDialog = true
                            },
                            onNewMemoClick = {
                                isNewDraftMemo = true
                                showNewDraftDialog = true
                            },
                            onFillTemplateClick = { showFillTemplateDialog = true },
                            onInspectCitationsClick = { draft ->
                                inspectingDraft = draft
                                showCitationInspectorDialog = true
                            },
                            onExportDraftClick = { draftId -> viewModel.exportDraft(draftId) },
                            onGenerateChronologyClick = { viewModel.generateChronology() },
                            onAddLocalNote = { title, content, tag, isPinned ->
                                viewModel.addLocalNote(matterId, title, content, tag, isPinned)
                                Toast.makeText(context, "تم حفظ الملاحظة في قاعدة البيانات المحلية (Room)", Toast.LENGTH_SHORT).show()
                            },
                            onTogglePinNote = { id, currentPin ->
                                viewModel.togglePinLocalNote(id, currentPin)
                            },
                            onDeleteLocalNote = { id ->
                                viewModel.deleteLocalNote(id)
                                Toast.makeText(context, "تم حذف الملاحظة", Toast.LENGTH_SHORT).show()
                            },
                            onScanDocumentClick = {
                                navController.navigate("scanner/$matterId")
                            },
                            onDeleteLocalScan = { scanId ->
                                viewModel.deleteScannedDocument(scanId)
                                Toast.makeText(context, "تم حذف المسح الضوئي", Toast.LENGTH_SHORT).show()
                            },
                            onUpdateMatterTags = { mId, newTags ->
                                viewModel.updateMatterTags(mId, newTags)
                                Toast.makeText(context, "تم تحديث وسوم القضية", Toast.LENGTH_SHORT).show()
                            },
                            onUpdateScanTags = { scanId, newTags ->
                                viewModel.updateScanTags(scanId, newTags)
                                Toast.makeText(context, "تم تحديث تصنيف المستند", Toast.LENGTH_SHORT).show()
                            },
                            onSyncDeadlineToCalendar = { deadline ->
                                viewModel.syncDeadlineToCalendar(context, deadline, selectedMatter?.matterLabel ?: "قضية", selectedMatter?.court)
                            },
                            onSyncHearingToCalendar = { hearing ->
                                viewModel.syncHearingToCalendar(context, hearing, selectedMatter?.matterLabel ?: "قضية", selectedMatter?.court)
                            },
                            isSummarizing = isSummarizing,
                            summaryResult = summaryResult,
                            onSummarizeCaseClick = { viewModel.summarizeCase() },
                            onSummarizeDocumentClick = { documentId -> viewModel.summarizeDocument(documentId) },
                            onClearSummaryResult = { viewModel.clearSummaryResult() },
                            isGeneratingBriefing = isGeneratingBriefing,
                            onGenerateBriefingClick = { viewModel.generateHearingBriefing() },
                            isCheckingConflicts = isCheckingConflicts,
                            conflictCheckResult = conflictCheckResult,
                            onCheckConflicts = { names -> viewModel.checkConflicts(names, excludeMatterId = matterId) },
                            onClearConflictCheckResult = { viewModel.clearConflictCheckResult() }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.text)
                        }
                    }
                }

                composable(
                    route = "scanner/{matterId}",
                    arguments = listOf(navArgument("matterId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val matterId = backStackEntry.arguments?.getString("matterId") ?: ""
                    DocumentScannerScreen(
                        matters = matters,
                        preselectedMatterId = matterId,
                        onBackClick = { navController.popBackStack() },
                        onSaveScannedDocument = { scanDoc, imageBytes ->
                            viewModel.saveScannedDocument(scanDoc, imageBytes)
                            Toast.makeText(context, "تم حفظ المستند الممسوح ضوئياً محلياً ✓", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.Roll.route) {
                    val selectedDate by viewModel.selectedRollDate.collectAsStateWithLifecycle()
                    val dayHearings by viewModel.dayHearings.collectAsStateWithLifecycle()
                    val monthHearingDates by viewModel.monthHearingDates.collectAsStateWithLifecycle()

                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        HearingRollScreen(
                            dayHearings = dayHearings,
                            monthHearingDates = monthHearingDates,
                            selectedDate = selectedDate,
                            onSelectDate = { date -> viewModel.selectRollDate(date) },
                            onNavigateToMatter = { matterId ->
                                viewModel.loadMatter(matterId)
                                navController.navigate("matter_detail/$matterId")
                            }
                        )
                    }
                }

                composable(Screen.Deadlines.route) {
                    val topDeadlines = stats.topProvisionalDeadlines

                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        DeadlinesListScreen(
                            deadlines = topDeadlines,
                            isLoading = false,
                            onConfirmDeadline = { deadlineId -> viewModel.confirmDeadline(deadlineId) },
                            onNavigateToMatter = { matterId ->
                                viewModel.loadMatter(matterId)
                                navController.navigate("matter_detail/$matterId")
                            }
                        )
                    }
                }

                composable(Screen.Review.route) {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        ReviewQueueScreen(
                            extractions = pendingExtractions,
                            isLoading = false,
                            onReviewExtraction = { id, action, corrected ->
                                viewModel.reviewExtraction(id, action, corrected)
                            }
                        )
                    }
                }

                composable(Screen.Research.route) {
                    val isSearching by viewModel.isSearchingResearch.collectAsStateWithLifecycle()
                    val researchResult by viewModel.researchResult.collectAsStateWithLifecycle()

                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        ResearchScreen(
                            onSearch = { q -> viewModel.performResearch(q) },
                            isSearching = isSearching,
                            researchResult = researchResult,
                            onNavigateToCorpus = { navController.navigate(Screen.Corpus.route) }
                        )
                    }
                }

                composable(Screen.Archive.route) {
                    val searchSummaries by viewModel.searchSummaries.collectAsStateWithLifecycle()
                    val searchNotes by viewModel.searchNotes.collectAsStateWithLifecycle()
                    val searchScans by viewModel.searchScans.collectAsStateWithLifecycle()
                    val localSearchQuery by viewModel.localSearchQuery.collectAsStateWithLifecycle()
                    val archiveSearchResult by viewModel.archiveSearchResult.collectAsStateWithLifecycle()
                    val isSearchingArchive by viewModel.isSearchingArchive.collectAsStateWithLifecycle()

                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        ArchiveSearchScreen(
                            allMatters = matters,
                            localSearchQuery = localSearchQuery,
                            searchSummaries = searchSummaries,
                            searchNotes = searchNotes,
                            searchScans = searchScans,
                            onSearchQueryChange = { q -> viewModel.setLocalSearchQuery(q) },
                            onNavigateToMatter = { matterId ->
                                viewModel.loadMatter(matterId)
                                navController.navigate("matter_detail/$matterId")
                            },
                            archiveSearchResult = archiveSearchResult,
                            isSearchingArchive = isSearchingArchive,
                            onSearchArchiveRemote = { q -> viewModel.searchArchiveRemote(q) }
                        )
                    }
                }

                composable(Screen.Corpus.route) {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        LegalCorpusScreen(
                            authorities = authorities,
                            isLoading = false,
                            onOpenAddAuthorityDialog = { showAddAuthorityDialog = true },
                            onVerifyAuthority = { id -> viewModel.verifyAuthority(id) }
                        )
                    }
                }

                composable(Screen.Templates.route) {
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshDashboard() }) {
                        TemplatesScreen(
                            templates = templates,
                            isLoading = false,
                            onOpenAddTemplateDialog = { showAddTemplateDialog = true }
                        )
                    }
                }
            }
        }
    }

    // ==================== MORE MENU BOTTOM SHEET ====================
    if (showMoreMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenuSheet = false },
            containerColor = colors.card
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "الأقسام الإضافية",
                    color = colors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                MORE_NAV_ITEMS.forEach { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreMenuSheet = false
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.inset
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(item.icon, contentDescription = null, tint = colors.accent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.title,
                                color = colors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 8.dp))

                // Theme Setting Section
                Text(
                    text = "مظهر التطبيق والسمات",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf(
                        Triple(AppThemeSetting.SYSTEM, "تلقائي (النظام)", Icons.Default.BrightnessAuto),
                        Triple(AppThemeSetting.LIGHT, "فاتح", Icons.Default.LightMode),
                        Triple(AppThemeSetting.DARK, "داكن (قراءة)", Icons.Default.DarkMode)
                    )

                    themeOptions.forEach { (setting, label, icon) ->
                        val isSelected = themeSetting == setting
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setThemeSetting(setting) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.heroBg else colors.inset,
                            border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isSelected) colors.accent else colors.textDim, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) colors.heroText else colors.text2,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // ==================== DIALOGS ====================

    if (showNewMatterDialog) {
        NewMatterDialog(
            onDismiss = { showNewMatterDialog = false },
            onSubmit = { newMatter ->
                showNewMatterDialog = false
                viewModel.createMatter(newMatter)
            }
        )
    }

    val currentSelectedMatter by viewModel.selectedMatter.collectAsStateWithLifecycle()
    if (showEditMatterDialog && currentSelectedMatter != null) {
        EditMatterDialog(
            matter = currentSelectedMatter!!,
            onDismiss = { showEditMatterDialog = false },
            onSubmit = { updated ->
                showEditMatterDialog = false
                viewModel.updateMatter(updated)
            }
        )
    }

    if (showPartyDialog && currentSelectedMatter != null) {
        PartyDialog(
            initialParty = editingParty,
            matterId = currentSelectedMatter!!.id,
            onDismiss = { showPartyDialog = false },
            onSubmit = { party ->
                showPartyDialog = false
                if (editingParty == null) viewModel.addParty(party)
                else viewModel.updateParty(party)
            }
        )
    }

    if (showRecordHearingDialog && currentSelectedMatter != null) {
        RecordHearingDialog(
            matterId = currentSelectedMatter!!.id,
            onDismiss = { showRecordHearingDialog = false },
            onSubmit = { hearing ->
                showRecordHearingDialog = false
                viewModel.recordHearing(hearing)
            }
        )
    }

    if (showProposeDeadlineDialog && currentSelectedMatter != null) {
        val deadlineRules by viewModel.deadlineRules.collectAsStateWithLifecycle()
        ProposeDeadlineDialog(
            matterId = currentSelectedMatter!!.id,
            rules = deadlineRules,
            onDismiss = { showProposeDeadlineDialog = false },
            onSubmit = { ruleId, triggerEvent, triggerDate ->
                showProposeDeadlineDialog = false
                viewModel.proposeDeadline(ruleId, triggerEvent, triggerDate)
            }
        )
    }

    if (showUploadDocsDialog && currentSelectedMatter != null) {
        UploadDocsDialog(
            matterId = currentSelectedMatter!!.id,
            onDismiss = { showUploadDocsDialog = false },
            onSubmit = { filename, docType, text ->
                showUploadDocsDialog = false
                viewModel.uploadDocument(currentSelectedMatter!!.id, filename, docType, text)
            }
        )
    }

    if (showNewDraftDialog && currentSelectedMatter != null) {
        NewDraftDialog(
            matterId = currentSelectedMatter!!.id,
            authorities = authorities,
            isMemo = isNewDraftMemo,
            onDismiss = { showNewDraftDialog = false },
            onSubmit = { docType, instructions, claims, authIds ->
                showNewDraftDialog = false
                viewModel.generateDraft(docType, instructions, claims, authIds)
            }
        )
    }

    if (showFillTemplateDialog && currentSelectedMatter != null) {
        FillTemplateDialog(
            templates = templates,
            onDismiss = { showFillTemplateDialog = false },
            onSubmit = { templateId, values ->
                showFillTemplateDialog = false
                viewModel.fillTemplate(templateId, values)
            }
        )
    }

    if (showCitationInspectorDialog && inspectingDraft != null) {
        val draftCitations by viewModel.draftCitations.collectAsStateWithLifecycle()
        val isLoadingCitations by viewModel.isLoadingCitations.collectAsStateWithLifecycle()
        val draftId = inspectingDraft!!.id
        LaunchedEffect(draftId) { viewModel.loadDraftCitations(draftId) }

        CitationInspectorDialog(
            draft = inspectingDraft!!,
            citations = draftCitations,
            isLoading = isLoadingCitations,
            onVerify = { citationId, status -> viewModel.verifyCitation(citationId, status, draftId) },
            onDismiss = {
                showCitationInspectorDialog = false
                inspectingDraft = null
                viewModel.clearDraftCitations()
            }
        )
    }

    if (showAddAuthorityDialog) {
        AddAuthorityDialog(
            onDismiss = { showAddAuthorityDialog = false },
            onSubmit = { auth ->
                showAddAuthorityDialog = false
                viewModel.addAuthority(auth)
            }
        )
    }

    if (showAddTemplateDialog) {
        AddTemplateDialog(
            onDismiss = { showAddTemplateDialog = false },
            onSubmit = { tmpl ->
                showAddTemplateDialog = false
                viewModel.addTemplate(tmpl)
            }
        )
    }
}
