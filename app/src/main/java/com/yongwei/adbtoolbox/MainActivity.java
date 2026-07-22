package com.yongwei.adbtoolbox;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.flyfishxu.kadb.Kadb;
import com.flyfishxu.kadb.shell.AdbShellPacket;
import com.flyfishxu.kadb.shell.AdbShellResponse;
import com.flyfishxu.kadb.shell.AdbShellStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wireless ADB Toolbox v1.1.
 *
 * The interface is constructed programmatically so the APK can be rebuilt without
 * any third-party UI framework. ADB transport is provided by kadb-android.
 */
public class MainActivity extends Activity {
    private static final int PICK_APK_REQUEST = 2001;
    private static final int SAVE_LOG_REQUEST = 2002;
    private static final int MAX_LOG_CHARS = 180000;

    private static final int PAGE_PAIRING = 0;
    private static final int PAGE_LEGACY = 1;
    private static final int PAGE_APK = 2;
    private static final int PAGE_SHELL = 3;
    private static final int PAGE_DEVICE = 4;
    private static final int PAGE_LOG = 5;

    private static final int BG = Color.rgb(14, 19, 25);
    private static final int PANEL = Color.rgb(24, 35, 43);
    private static final int PANEL_2 = Color.rgb(18, 27, 34);
    private static final int BORDER = Color.rgb(58, 73, 82);
    private static final int TEXT = Color.rgb(236, 241, 244);
    private static final int MUTED = Color.rgb(166, 177, 184);
    private static final int ACCENT = Color.rgb(32, 151, 112);
    private static final int ACCENT_DARK = Color.rgb(20, 112, 84);
    private static final int DANGER = Color.rgb(190, 79, 87);
    private static final int TERMINAL = Color.rgb(5, 12, 16);

    private final Charset utf8 = Charset.forName("UTF-8");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private volatile Kadb adb;
    private volatile File selectedApk;
    private volatile String connectedEndpoint = "";

    private LinearLayout root;
    private FrameLayout pageHost;
    private View[] pages;
    private Button[] tabs;

    private EditText pairingIpInput;
    private EditText pairingConnectPortInput;
    private EditText pairPortInput;
    private EditText pairCodeInput;
    private EditText legacyIpInput;
    private EditText legacyPortInput;
    private EditText remotePathInput;
    private EditText shellInput;

    private TextView statusDot;
    private TextView statusText;
    private TextView statusEndpointText;
    private TextView apkNameText;
    private TextView apkProgressText;
    private TextView deviceInfoText;
    private TextView logText;

    private CheckBox deleteRemoteCheck;
    private CheckBox pauseAutoScrollCheck;
    private ProgressBar apkProgress;
    private ScrollView logScroll;
    private Button pushOnlyButton;
    private Button pushInstallButton;
    private SharedPreferences prefs;
    private String languageCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("adb_toolbox", MODE_PRIVATE);
        languageCode = resolveLanguage();
        configureWindowInsets();
        buildInterface();
        restoreInputs();
        showPage(PAGE_PAIRING);
        setConnectionState(s("未连接", "Not connected", "未連線", "Sin conexión", "未接続", "연결되지 않음"),
                s("尚未建立 ADB 通道", "No ADB channel established", "尚未建立 ADB 通道", "No se ha establecido un canal ADB", "ADB チャネルは未確立です", "ADB 채널이 설정되지 않았습니다"), false);
        appendLog(s("无线 ADB 工具箱 v1.1 已启动。请仅连接你拥有或获授权管理的设备。", "Wireless ADB Toolbox v1.1 started. Connect only to devices you own or are authorized to manage.", "無線 ADB 工具箱 v1.1 已啟動。請僅連線至您擁有或獲授權管理的裝置。", "Wireless ADB Toolbox v1.1 se inició. Conéctese solo a dispositivos propios o autorizados.", "Wireless ADB Toolbox v1.1 を起動しました。所有または管理を許可された端末だけに接続してください。", "Wireless ADB Toolbox v1.1이 시작되었습니다. 본인이 소유하거나 관리 권한이 있는 기기에만 연결하세요。"));
    }

    private void configureWindowInsets() {
        Window window = getWindow();
        try {
            window.getDecorView().setSystemUiVisibility(0);
            window.clearFlags(0x00000400); // FLAG_FULLSCREEN
            window.clearFlags(0x00000200); // FLAG_LAYOUT_NO_LIMITS
            window.clearFlags(0x04000000); // FLAG_TRANSLUCENT_STATUS
            window.clearFlags(0x08000000); // FLAG_TRANSLUCENT_NAVIGATION
        } catch (Throwable ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Throwable ignored) { }
        try {
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
        } catch (Throwable ignored) { }
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(false);
        installWindowInsets();

        LinearLayout header = vertical(dp(18), dp(10), dp(18), dp(8));
        TextView title = text(s("无线 ADB 工具箱", "Wireless ADB Toolbox", "無線 ADB 工具箱", "Caja de herramientas ADB inalámbrica", "ワイヤレス ADB ツールボックス", "무선 ADB 도구 상자"), 28, TEXT, true);
        TextView subtitle = text(s("配对 · 连接 · APK 推送 · Shell · 设备信息", "Pair · Connect · APK push · Shell · Device info", "配對 · 連線 · APK 推送 · Shell · 裝置資訊", "Emparejar · Conectar · Enviar APK · Shell · Información", "ペアリング・接続・APK 転送・Shell・端末情報", "페어링 · 연결 · APK 전송 · Shell · 기기 정보"), 15, MUTED, false);
        header.addView(title, matchWrap());
        header.addView(subtitle, matchWrapTop(dp(3)));
        root.addView(header, matchWrap());

        root.addView(buildStatusCard(), matchWrapMargins(dp(14), dp(4), dp(14), dp(8)));
        root.addView(buildTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        pageHost = new FrameLayout(this);
        pages = new View[6];
        pages[PAGE_PAIRING] = buildPairingPage();
        pages[PAGE_LEGACY] = buildLegacyConnectionPage();
        pages[PAGE_APK] = buildApkPage();
        pages[PAGE_SHELL] = buildShellPage();
        pages[PAGE_DEVICE] = buildDevicePage();
        pages[PAGE_LOG] = buildLogPage();
        for (int i = 0; i < pages.length; i++) {
            pageHost.addView(pages[i], new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void installWindowInsets() {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    top = bars.top;
                    bottom = Math.max(bars.bottom, ime.bottom);
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                view.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
        root.post(new Runnable() {
            @Override public void run() { root.requestApplyInsets(); }
        });
    }

    private View buildStatusCard() {
        LinearLayout card = vertical(dp(14), dp(11), dp(14), dp(11));
        card.setBackground(panelDrawable(PANEL, BORDER, 16));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = text("●", 18, DANGER, true);
        statusText = text(s("未连接", "Not connected", "未連線", "Sin conexión", "未接続", "연결되지 않음"), 17, TEXT, true);
        row.addView(statusDot, wrapWrap());
        row.addView(statusText, wrapWrapLeft(dp(8)));
        card.addView(row, matchWrap());

        statusEndpointText = text(s("尚未建立 ADB 通道", "No ADB channel established", "尚未建立 ADB 通道", "No se ha establecido un canal ADB", "ADB チャネルは未確立です", "ADB 채널이 설정되지 않았습니다"), 13, MUTED, false);
        statusEndpointText.setSingleLine(true);
        card.addView(statusEndpointText, matchWrapTop(dp(3)));
        return card;
    }

    private View buildTabs() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(4), dp(10), dp(4));
        scroller.addView(bar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tabs = new Button[6];
        String[] labels = new String[]{"Android 11+", s("传统 ADB", "Legacy ADB", "傳統 ADB", "ADB clásico", "従来の ADB", "기존 ADB"), "APK", "Shell", s("设备", "Device", "裝置", "Dispositivo", "端末", "기기"), s("日志", "Logs", "日誌", "Registros", "ログ", "로그")};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            Button button = button(labels[i], ACCENT_DARK);
            button.setMinWidth(dp(82));
            button.setTextSize(14);
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showPage(page); }
            });
            tabs[i] = button;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(40));
            lp.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(button, lp);
        }
        return scroller;
    }

    private View buildPairingPage() {
        LinearLayout body = pageBody();
        body.addView(sectionTitle(s("1. Android 11+ 无线配对", "1. Android 11+ wireless pairing", "1. Android 11+ 無線配對", "1. Emparejamiento inalámbrico Android 11+", "1. Android 11+ ワイヤレス ペアリング", "1. Android 11+ 무선 페어링")), matchWrap());
        body.addView(help(s("首次连接通常先在远程手机的“使用配对码配对设备”页面完成配对。配对端口与连接端口通常不同。", "For a first connection, pair on the remote phone's 'Pair device with pairing code' screen. Pairing and connection ports usually differ.", "首次連線通常先在遠端手機的「使用配對碼配對裝置」頁面完成配對。配對連接埠與連線連接埠通常不同。", "Para la primera conexión, empareje en la pantalla 'Vincular dispositivo con código de emparejamiento'. Los puertos suelen ser distintos.", "初回接続では、リモート端末の「ペア設定コードによるデバイスのペア設定」画面でペアリングします。ポートは通常異なります。", "처음 연결할 때는 원격 기기의 '페어링 코드로 기기 페어링' 화면에서 페어링하세요. 페어링과 연결 포트는 보통 다릅니다。")), matchWrapTop(dp(5)));
        body.addView(label(s("设备 IP 地址", "Device IP address", "裝置 IP 位址", "Dirección IP del dispositivo", "端末の IP アドレス", "기기 IP 주소")), matchWrapTop(dp(14)));
        pairingIpInput = input(s("例如 192.168.1.88", "e.g. 192.168.1.88", "例如 192.168.1.88", "p. ej., 192.168.1.88", "例: 192.168.1.88", "예: 192.168.1.88"), false, false);
        body.addView(pairingIpInput, matchHeightTop(dp(56), dp(6)));

        LinearLayout pairRow = new LinearLayout(this);
        pairRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout pairPortCol = vertical(0, 0, 0, 0);
        pairPortCol.addView(label(s("配对端口", "Pairing port", "配對連接埠", "Puerto de emparejamiento", "ペアリング ポート", "페어링 포트")), matchWrap());
        pairPortInput = input(s("配对页面端口", "Port on pairing screen", "配對頁面的連接埠", "Puerto de la pantalla de emparejamiento", "ペアリング画面のポート", "페어링 화면의 포트"), true, false);
        pairPortCol.addView(pairPortInput, matchHeightTop(dp(56), dp(6)));
        LinearLayout codeCol = vertical(0, 0, 0, 0);
        codeCol.addView(label(s("6 位配对码", "6-digit pairing code", "6 位配對碼", "Código de emparejamiento de 6 dígitos", "6 桁のペアリング コード", "6자리 페어링 코드")), matchWrap());
        pairCodeInput = input("123456", true, true);
        codeCol.addView(pairCodeInput, matchHeightTop(dp(56), dp(6)));
        pairRow.addView(pairPortCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        codeLp.setMargins(dp(10), 0, 0, 0);
        pairRow.addView(codeCol, codeLp);
        body.addView(pairRow, matchWrapTop(dp(12)));

        Button pair = button(s("第一步：配对", "Step 1: Pair", "步驟 1：配對", "Paso 1: Emparejar", "手順 1: ペアリング", "1단계: 페어링"), ACCENT);
        pair.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pairDevice(); }
        });
        body.addView(pair, matchHeightTop(dp(54), dp(14)));

        body.addView(divider(), matchHeightTop(dp(1), dp(18)));
        body.addView(sectionTitle(s("2. 建立 ADB 连接", "2. Establish ADB connection", "2. 建立 ADB 連線", "2. Establecer conexión ADB", "2. ADB 接続を確立", "2. ADB 연결 설정")), matchWrapTop(dp(18)));
        body.addView(help(s("配对成功后，返回无线调试主页面，把那里显示的连接端口填在下方。", "After pairing, return to the Wireless debugging main screen and enter its connection port below.", "配對成功後，返回無線偵錯主頁面，將其顯示的連線連接埠填入下方。", "Después de emparejar, vuelva a la pantalla principal de Depuración inalámbrica e introduzca el puerto de conexión.", "ペアリング後、ワイヤレス デバッグのメイン画面に戻り、表示された接続ポートを入力します。", "페어링 후 무선 디버깅 기본 화면으로 돌아가 표시된 연결 포트를 입력하세요。")), matchWrapTop(dp(5)));
        body.addView(label(s("连接端口", "Connection port", "連線連接埠", "Puerto de conexión", "接続ポート", "연결 포트")), matchWrapTop(dp(14)));
        pairingConnectPortInput = input(s("无线调试主页面端口", "Port on Wireless debugging screen", "無線偵錯主頁面的連接埠", "Puerto de la pantalla de depuración inalámbrica", "ワイヤレス デバッグ画面のポート", "무선 디버깅 화면의 포트"), true, false);
        body.addView(pairingConnectPortInput, matchHeightTop(dp(56), dp(6)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = button(s("连接", "Connect", "連線", "Conectar", "接続", "연결"), ACCENT);
        Button disconnect = button(s("断开", "Disconnect", "中斷連線", "Desconectar", "切断", "연결 해제"), DANGER);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { connectDevice(
                    pairingIpInput.getText().toString().trim(),
                    pairingConnectPortInput.getText().toString(), "Android 11+"); }
        });
        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { disconnectDevice(); }
        });
        buttons.addView(connect, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams disconnectLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        disconnectLp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(disconnect, disconnectLp);
        body.addView(buttons, matchWrapTop(dp(14)));
        return pageScroll(body);
    }

    private View buildLegacyConnectionPage() {
        LinearLayout body = pageBody();
        body.addView(sectionTitle(s("传统无线 ADB（Android 10 及以下）", "Legacy wireless ADB (Android 10 and below)", "傳統無線 ADB（Android 10 及以下）", "ADB inalámbrico clásico (Android 10 e inferiores)", "従来のワイヤレス ADB（Android 10 以下）", "기존 무선 ADB(Android 10 이하)")), matchWrap());
        body.addView(help(s("适用于已通过 USB 调试执行 adb tcpip 5555，或设备已开启传统 TCP ADB 的情况。请确保两台设备在同一局域网，仅连接已获授权的设备。", "Use this after running adb tcpip 5555 through USB debugging, or when the device already has legacy TCP ADB enabled. Both devices must be on the same LAN.", "適用於已透過 USB 偵錯執行 adb tcpip 5555，或裝置已開啟傳統 TCP ADB 的情況。請確保兩台裝置在同一區域網路。", "Úselo después de ejecutar adb tcpip 5555 mediante depuración USB, o si el dispositivo ya tiene ADB TCP clásico activado. Ambos dispositivos deben estar en la misma LAN.", "USB デバッグで adb tcpip 5555 を実行した場合、または端末で従来の TCP ADB が有効な場合に使用します。両方の端末を同じ LAN に接続してください。", "USB 디버깅으로 adb tcpip 5555를 실행했거나 기기에 기존 TCP ADB가 활성화된 경우 사용합니다. 두 기기는 같은 LAN에 있어야 합니다。")), matchWrapTop(dp(5)));
        body.addView(label(s("设备 IP 地址", "Device IP address", "裝置 IP 位址", "Dirección IP del dispositivo", "端末の IP アドレス", "기기 IP 주소")), matchWrapTop(dp(14)));
        legacyIpInput = input(s("例如 192.168.1.88", "e.g. 192.168.1.88", "例如 192.168.1.88", "p. ej., 192.168.1.88", "例: 192.168.1.88", "예: 192.168.1.88"), false, false);
        body.addView(legacyIpInput, matchHeightTop(dp(56), dp(6)));
        body.addView(label(s("ADB TCP 端口", "ADB TCP port", "ADB TCP 連接埠", "Puerto TCP de ADB", "ADB TCP ポート", "ADB TCP 포트")), matchWrapTop(dp(14)));
        legacyPortInput = input("5555", true, false);
        body.addView(legacyPortInput, matchHeightTop(dp(56), dp(6)));
        body.addView(help(s("默认端口为 5555。如设备使用了其他 TCP ADB 端口，请改为实际端口。", "The default port is 5555. Change it if the device uses another TCP ADB port.", "預設連接埠為 5555。如裝置使用其他 TCP ADB 連接埠，請改為實際連接埠。", "El puerto predeterminado es 5555. Cámbielo si el dispositivo usa otro puerto TCP de ADB.", "既定のポートは 5555 です。端末が別の TCP ADB ポートを使用する場合は変更してください。", "기본 포트는 5555입니다. 기기에서 다른 TCP ADB 포트를 사용하면 변경하세요。")), matchWrapTop(dp(7)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = button(s("连接传统 ADB", "Connect legacy ADB", "連線傳統 ADB", "Conectar ADB clásico", "従来の ADB に接続", "기존 ADB 연결"), ACCENT);
        Button disconnect = button(s("断开", "Disconnect", "中斷連線", "Desconectar", "切断", "연결 해제"), DANGER);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { connectDevice(
                    legacyIpInput.getText().toString().trim(),
                    legacyPortInput.getText().toString(), s("传统无线 ADB", "Legacy wireless ADB", "傳統無線 ADB", "ADB inalámbrico clásico", "従来のワイヤレス ADB", "기존 무선 ADB")); }
        });
        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { disconnectDevice(); }
        });
        buttons.addView(connect, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams disconnectLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        disconnectLp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(disconnect, disconnectLp);
        body.addView(buttons, matchWrapTop(dp(18)));
        return pageScroll(body);
    }

    private View buildApkPage() {
        LinearLayout body = pageBody();
        body.addView(sectionTitle(s("APK 推送与安装", "APK transfer and install", "APK 推送與安裝", "Transferencia e instalación de APK", "APK 転送とインストール", "APK 전송 및 설치")), matchWrap());
        apkNameText = text(s("尚未选择 APK", "No APK selected", "尚未選擇 APK", "No se seleccionó APK", "APK が選択されていません", "APK가 선택되지 않았습니다"), 15, MUTED, false);
        body.addView(apkNameText, matchWrapTop(dp(8)));
        Button select = button(s("从本机存储选择 APK", "Choose APK from device storage", "從本機儲存空間選擇 APK", "Elegir APK del almacenamiento", "端末ストレージから APK を選択", "기기 저장소에서 APK 선택"), ACCENT);
        select.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openApkPicker(); }
        });
        body.addView(select, matchHeightTop(dp(54), dp(12)));

        body.addView(label(s("远程保存路径", "Remote save path", "遠端儲存路徑", "Ruta de guardado remota", "リモート保存パス", "원격 저장 경로")), matchWrapTop(dp(18)));
        remotePathInput = input("/data/local/tmp/app.apk", false, false);
        body.addView(remotePathInput, matchHeightTop(dp(56), dp(6)));

        deleteRemoteCheck = new CheckBox(this);
        deleteRemoteCheck.setText(s("安装成功后删除远程临时 APK", "Delete temporary remote APK after installation", "安裝成功後刪除遠端暫存 APK", "Eliminar APK temporal remoto después de instalar", "インストール後にリモートの一時 APK を削除", "설치 후 원격 임시 APK 삭제"));
        deleteRemoteCheck.setTextColor(TEXT);
        deleteRemoteCheck.setTextSize(15);
        tintCheckBox(deleteRemoteCheck);
        body.addView(deleteRemoteCheck, matchWrapTop(dp(8)));

        apkProgress = new ProgressBar(this);
        apkProgress.setIndeterminate(true);
        apkProgress.setVisibility(View.GONE);
        try {
            Method m = ProgressBar.class.getMethod("setIndeterminateTintList", ColorStateList.class);
            m.invoke(apkProgress, ColorStateList.valueOf(ACCENT));
        } catch (Throwable ignored) { }
        body.addView(apkProgress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        apkProgressText = text(s("等待操作", "Waiting", "等待操作", "En espera", "待機中", "대기 중"), 13, MUTED, false);
        body.addView(apkProgressText, matchWrapTop(dp(3)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        pushOnlyButton = button(s("仅推送", "Push only", "僅推送", "Solo enviar", "転送のみ", "전송만"), ACCENT_DARK);
        pushInstallButton = button(s("推送并安装", "Push and install", "推送並安裝", "Enviar e instalar", "転送してインストール", "전송 및 설치"), ACCENT);
        pushOnlyButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pushSelectedApk(false); }
        });
        pushInstallButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pushSelectedApk(true); }
        });
        actions.addView(pushOnlyButton, new LinearLayout.LayoutParams(0, dp(56), 1f));
        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        installLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(pushInstallButton, installLp);
        body.addView(actions, matchWrapTop(dp(14)));
        body.addView(help(s("文件默认推送到 /data/local/tmp。选择“推送并安装”时会执行 pm install -r。", "Files are pushed to /data/local/tmp by default. Push and install runs pm install -r.", "檔案預設推送至 /data/local/tmp。選擇「推送並安裝」時會執行 pm install -r。", "Los archivos se envían a /data/local/tmp de forma predeterminada. Enviar e instalar ejecuta pm install -r.", "ファイルは既定で /data/local/tmp に転送されます。「転送してインストール」は pm install -r を実行します。", "파일은 기본적으로 /data/local/tmp에 전송됩니다. 전송 및 설치는 pm install -r을 실행합니다。")), matchWrapTop(dp(12)));
        return pageScroll(body);
    }

    private View buildShellPage() {
        LinearLayout body = pageBody();
        body.addView(sectionTitle(s("Shell 命令", "Shell commands", "Shell 命令", "Comandos Shell", "Shell コマンド", "Shell 명령")), matchWrap());
        body.addView(help(s("支持执行任意已授权的 ADB shell 命令。执行后会自动打开日志页面。", "Run any authorized ADB shell command. The Logs page opens automatically afterwards.", "支援執行任何已授權的 ADB shell 命令。執行後會自動開啟日誌頁面。", "Ejecute cualquier comando ADB shell autorizado. La página Registros se abre después.", "許可された任意の ADB shell コマンドを実行できます。実行後にログ画面を開きます。", "권한이 있는 모든 ADB shell 명령을 실행할 수 있습니다. 실행 후 로그 페이지가 자동으로 열립니다。")), matchWrapTop(dp(5)));
        shellInput = input(s("例如 pm list packages", "e.g. pm list packages", "例如 pm list packages", "p. ej., pm list packages", "例: pm list packages", "예: pm list packages"), false, false);
        shellInput.setGravity(Gravity.TOP | Gravity.LEFT);
        shellInput.setSingleLine(false);
        shellInput.setMinLines(4);
        body.addView(shellInput, matchHeightTop(dp(126), dp(12)));

        LinearLayout quickRow1 = new LinearLayout(this);
        quickRow1.setOrientation(LinearLayout.HORIZONTAL);
        quickRow1.addView(quickButton(s("第三方包", "Third-party apps", "第三方套件", "Apps de terceros", "サードパーティ アプリ", "서드 파티 앱"), "pm list packages -3"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        quickRow1.addView(quickButton(s("分辨率", "Resolution", "解析度", "Resolución", "解像度", "해상도"), "wm size"), weightedButtonLp());
        quickRow1.addView(quickButton(s("电池", "Battery", "電池", "Batería", "バッテリー", "배터리"), "dumpsys battery"), weightedButtonLp());
        body.addView(quickRow1, matchWrapTop(dp(10)));

        LinearLayout quickRow2 = new LinearLayout(this);
        quickRow2.setOrientation(LinearLayout.HORIZONTAL);
        quickRow2.addView(quickButton(s("型号", "Model", "型號", "Modelo", "モデル", "모델"), "getprop ro.product.model"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        quickRow2.addView(quickButton(s("截图到设备", "Screenshot on device", "擷取螢幕畫面至裝置", "Captura en el dispositivo", "端末にスクリーンショット", "기기에 스크린샷 저장"), "screencap -p /sdcard/adbtoolbox-screen.png"), weightedButtonLp());
        body.addView(quickRow2, matchWrapTop(dp(8)));

        Button execute = button(s("执行命令并查看日志", "Run command and view logs", "執行命令並檢視日誌", "Ejecutar comando y ver registros", "コマンドを実行してログを表示", "명령 실행 및 로그 보기"), ACCENT);
        execute.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { executeShell(shellInput.getText().toString().trim()); }
        });
        body.addView(execute, matchHeightTop(dp(56), dp(16)));
        return pageScroll(body);
    }

    private View buildDevicePage() {
        LinearLayout body = pageBody();
        body.addView(sectionTitle(s("设备信息", "Device information", "裝置資訊", "Información del dispositivo", "端末情報", "기기 정보")), matchWrap());
        Button refresh = button(s("刷新设备信息", "Refresh device information", "重新整理裝置資訊", "Actualizar información del dispositivo", "端末情報を更新", "기기 정보 새로고침"), ACCENT);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshDeviceInfo(); }
        });
        body.addView(refresh, matchHeightTop(dp(54), dp(10)));
        deviceInfoText = text(s("连接设备后点击“刷新设备信息”。", "Connect a device, then tap Refresh device information.", "連線裝置後點選「重新整理裝置資訊」。", "Conecte un dispositivo y toque Actualizar información.", "端末に接続してから「端末情報を更新」をタップしてください。", "기기를 연결한 후 기기 정보 새로고침을 누르세요。"), 15, TEXT, false);
        deviceInfoText.setTextIsSelectable(true);
        deviceInfoText.setTypeface(Typeface.MONOSPACE);
        deviceInfoText.setPadding(dp(14), dp(14), dp(14), dp(14));
        deviceInfoText.setBackground(panelDrawable(TERMINAL, BORDER, 12));
        body.addView(deviceInfoText, matchWrapTop(dp(12)));
        return pageScroll(body);
    }

    private View buildLogPage() {
        LinearLayout body = vertical(dp(12), dp(8), dp(12), dp(10));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        pauseAutoScrollCheck = new CheckBox(this);
        pauseAutoScrollCheck.setText(s("暂停滚动", "Pause scrolling", "暫停捲動", "Pausar desplazamiento", "スクロールを停止", "스크롤 일시 중지"));
        pauseAutoScrollCheck.setTextColor(TEXT);
        pauseAutoScrollCheck.setTextSize(13);
        tintCheckBox(pauseAutoScrollCheck);
        controls.addView(pauseAutoScrollCheck, new LinearLayout.LayoutParams(0, dp(44), 1f));
        controls.addView(logActionButton(s("复制", "Copy", "複製", "Copiar", "コピー", "복사"), new View.OnClickListener() {
            @Override public void onClick(View v) { copyLogs(); }
        }), logButtonLp());
        controls.addView(logActionButton(s("保存", "Save", "儲存", "Guardar", "保存", "저장"), new View.OnClickListener() {
            @Override public void onClick(View v) { createLogDocument(); }
        }), logButtonLp());
        controls.addView(logActionButton(s("清空", "Clear", "清除", "Borrar", "消去", "지우기"), new View.OnClickListener() {
            @Override public void onClick(View v) { logText.setText(""); toast(s("日志已清空", "Logs cleared", "日誌已清除", "Registros borrados", "ログを消去しました", "로그를 지웠습니다")); }
        }), logButtonLp());
        body.addView(controls, matchWrap());

        logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logScroll.setBackground(panelDrawable(TERMINAL, BORDER, 12));
        logText = text("", 12, Color.rgb(163, 232, 196), false);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logScroll.addView(logText, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(buildLanguageSelector(), matchWrapTop(dp(10)));
        return body;
    }

    private View buildLanguageSelector() {
        LinearLayout card = vertical(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(panelDrawable(PANEL, BORDER, 12));
        card.addView(sectionTitle(s("程序语言", "App language", "程式語言", "Idioma de la aplicación", "アプリの言語", "앱 언어")), matchWrap());
        card.addView(help(s("首次启动时会跟随系统语言；不支持的系统语言默认使用英语。修改后应用会立即重新加载。", "On first launch, the app follows the system language. Unsupported system languages use English. The app reloads after a change.", "首次啟動時會跟隨系統語言；不支援的系統語言預設使用英語。修改後應用程式會立即重新載入。", "En el primer inicio, la aplicación sigue el idioma del sistema. Los idiomas no admitidos usan inglés. La aplicación se recarga después del cambio.", "初回起動時はシステム言語に従います。非対応の言語では英語を使用します。変更後、アプリは再読み込みされます。", "처음 시작할 때 앱은 시스템 언어를 따릅니다. 지원하지 않는 시스템 언어는 영어를 사용합니다. 변경 후 앱이 다시 로드됩니다。")), matchWrapTop(dp(5)));
        Spinner selector = new Spinner(this);
        final String[] codes = new String[]{"system", "en", "zh-CN", "zh-TW", "es", "ja", "ko"};
        String[] names = new String[]{s("跟随系统", "Follow system", "跟隨系統", "Seguir sistema", "システムに従う", "시스템 설정 따르기"), "English (US / UK)", "简体中文", "繁體中文", "Español", "日本語", "한국어"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(adapter);
        String saved = prefs.getString("app_language", "system");
        int selected = 0;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(saved)) selected = i;
        selector.setSelection(selected, false);
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String choice = codes[position];
                if (!choice.equals(prefs.getString("app_language", "system"))) {
                    saveInputs();
                    prefs.edit().putString("app_language", choice).apply();
                    recreate();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        card.addView(selector, matchHeightTop(dp(52), dp(8)));
        return card;
    }

    private Button quickButton(String title, final String command) {
        Button b = button(title, ACCENT_DARK);
        b.setTextSize(12);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                shellInput.setText(command);
                executeShell(command);
            }
        });
        return b;
    }

    private Button logActionButton(String title, View.OnClickListener listener) {
        Button b = button(title, ACCENT_DARK);
        b.setTextSize(12);
        b.setOnClickListener(listener);
        return b;
    }

    private void showPage(int index) {
        if (pages == null || tabs == null) return;
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == index;
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(selected ? TEXT : MUTED);
            tabs[i].setBackground(panelDrawable(selected ? ACCENT_DARK : PANEL_2, selected ? ACCENT : BORDER, 10));
        }
        if (index == PAGE_LOG && pauseAutoScrollCheck != null && !pauseAutoScrollCheck.isChecked()) {
            logScroll.post(new Runnable() {
                @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
            });
        }
    }

    private void pairDevice() {
        final String host = pairingIpInput.getText().toString().trim();
        final Integer port = parsePort(pairPortInput.getText().toString());
        final String code = pairCodeInput.getText().toString().trim();
        if (!validHost(host) || port == null || !code.matches("\\d{6}")) {
            toast("请输入有效 IP、配对端口和 6 位配对码");
            return;
        }
        saveInputs();
        setConnectionState("正在配对…", host + ":" + port, false);
        appendLog("开始配对 " + host + ":" + port);
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    KadbBridge.pairBlocking(host, port.intValue(), code);
                    runUi(new Runnable() {
                        @Override public void run() {
                            pairCodeInput.setText("");
                            pairingConnectPortInput.requestFocus();
                            setConnectionState("配对成功，等待连接", "请填写无线调试主页面显示的连接端口", false);
                        }
                    });
                    appendLog("配对成功。下一步请输入无线调试主页面显示的连接端口。");
                } catch (Throwable e) {
                    fail("配对失败", e, true);
                }
            }
        });
    }

    private void connectDevice(final String host, String portValue, final String connectionType) {
        final Integer port = parsePort(portValue);
        if (!validHost(host) || port == null) {
            toast("请输入有效 IP 和连接端口");
            return;
        }
        saveInputs();
        setConnectionState("正在连接…", host + ":" + port, false);
        appendLog("连接" + connectionType + " " + host + ":" + port);
        worker.execute(new Runnable() {
            @Override public void run() {
                Kadb candidate = null;
                try {
                    candidate = KadbBridge.create(host, port.intValue());
                    AdbShellResponse probe = candidate.shell("echo ADB_TOOLBOX_CONNECTED");
                    if (!probe.getAllOutput().contains("ADB_TOOLBOX_CONNECTED")) {
                        throw new IllegalStateException("设备未返回连接探测结果");
                    }
                    Kadb old = adb;
                    adb = candidate;
                    connectedEndpoint = host + ":" + port;
                    candidate = null;
                    if (old != null) old.close();
                    runUi(new Runnable() {
                        @Override public void run() { setConnectionState("已连接", connectedEndpoint, true); }
                    });
                    appendLog(connectionType + "连接成功，ADB 通道已就绪。");
                } catch (Throwable e) {
                    if (candidate != null) {
                        try { candidate.close(); } catch (Throwable ignored) { }
                    }
                    fail("连接失败", e, true);
                }
            }
        });
    }

    private void disconnectDevice() {
        final Kadb old = adb;
        adb = null;
        connectedEndpoint = "";
        setConnectionState("未连接", "尚未建立 ADB 通道", false);
        appendLog("正在断开设备…");
        new Thread(new Runnable() {
            @Override public void run() {
                if (old != null) {
                    try { old.close(); } catch (Throwable ignored) { }
                }
                appendLog("已断开设备。");
            }
        }, "adb-disconnect").start();
    }

    private void openApkPicker() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{
                "application/vnd.android.package-archive", "application/octet-stream"
        });
        try {
            startActivityForResult(intent, PICK_APK_REQUEST);
        } catch (Throwable first) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("application/vnd.android.package-archive");
            startActivityForResult(fallback, PICK_APK_REQUEST);
        }
    }

    private void createLogDocument() {
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "adb-toolbox-log-" + stamp + ".txt");
        try {
            startActivityForResult(intent, SAVE_LOG_REQUEST);
        } catch (Throwable e) {
            toast("当前系统不支持系统文件保存器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_APK_REQUEST) {
            copySelectedApk(data.getData());
        } else if (requestCode == SAVE_LOG_REQUEST) {
            saveLogsToUri(data.getData());
        }
    }

    private void saveLogsToUri(final Uri uri) {
        final String snapshot = logText.getText().toString();
        worker.execute(new Runnable() {
            @Override public void run() {
                OutputStream out = null;
                try {
                    out = getContentResolver().openOutputStream(uri, "wt");
                    if (out == null) throw new IllegalStateException("无法创建日志文件");
                    out.write(snapshot.getBytes(utf8));
                    out.flush();
                    runUi(new Runnable() {
                        @Override public void run() { toast("日志已保存"); }
                    });
                } catch (Throwable e) {
                    fail("保存日志失败", e, false);
                } finally {
                    if (out != null) try { out.close(); } catch (Throwable ignored) { }
                }
            }
        });
    }

    private void copySelectedApk(final Uri uri) {
        final String originalName = queryDisplayName(uri);
        final String lower = originalName.toLowerCase(Locale.ROOT);
        final String safeName = sanitizeFilename(lower.endsWith(".apk") ? originalName : originalName + ".apk");
        File dir = new File(getCacheDir(), "selected_apk");
        if (!dir.exists()) dir.mkdirs();
        final File target = new File(dir, safeName);
        showApkProgress(true, "正在读取 " + originalName + "…");
        appendLog("读取本机文件：" + originalName);
        worker.execute(new Runnable() {
            @Override public void run() {
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    in = getContentResolver().openInputStream(uri);
                    out = new FileOutputStream(target);
                    if (in == null) throw new IllegalStateException("无法打开所选文件");
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                    out.flush();
                    if (target.length() <= 0) throw new IllegalStateException("所选文件为空");
                    selectedApk = target;
                    final String size = humanBytes(target.length());
                    runUi(new Runnable() {
                        @Override public void run() {
                            apkNameText.setText("已选择：" + originalName + "（" + size + "）");
                            remotePathInput.setText("/data/local/tmp/" + safeName);
                            showApkProgress(false, "文件已准备：" + size);
                        }
                    });
                    appendLog("本机 APK 已准备：" + target.getName() + "，" + size);
                } catch (Throwable e) {
                    runUi(new Runnable() {
                        @Override public void run() { showApkProgress(false, "读取失败"); }
                    });
                    fail("读取 APK 失败", e, false);
                } finally {
                    if (in != null) try { in.close(); } catch (Throwable ignored) { }
                    if (out != null) try { out.close(); } catch (Throwable ignored) { }
                }
            }
        });
    }

    private void pushSelectedApk(final boolean install) {
        final File file = selectedApk;
        final String remote = remotePathInput.getText().toString().trim();
        final boolean deleteAfterInstall = deleteRemoteCheck.isChecked();
        if (file == null || !file.exists()) {
            toast("请先选择 APK 文件");
            return;
        }
        if (!remote.startsWith("/") || remote.contains("\n") || remote.contains("\r")) {
            toast("请输入有效的远程绝对路径");
            return;
        }
        if (adb == null) {
            toast("请先连接远程设备");
            showPage(PAGE_PAIRING);
            return;
        }
        saveInputs();
        setApkButtonsEnabled(false);
        showApkProgress(true, "正在推送 " + humanBytes(file.length()) + "…");
        appendLog("开始 ADB push：" + file.getName() + " → " + remote + "（" + humanBytes(file.length()) + "）");
        worker.execute(new Runnable() {
            @Override public void run() {
                Kadb client = requireAdb();
                if (client == null) {
                    runUi(new Runnable() {
                        @Override public void run() {
                            setApkButtonsEnabled(true);
                            showApkProgress(false, "尚未连接设备");
                        }
                    });
                    return;
                }
                try {
                    client.push(file, remote, 420, file.lastModified());
                    appendLog("push 完成：" + remote);
                    if (install) {
                        runUi(new Runnable() {
                            @Override public void run() { showApkProgress(true, "推送完成，正在安装…"); }
                        });
                        String command = "pm install -r " + shellQuote(remote);
                        appendLog("$ " + command);
                        AdbShellResponse response = client.shell(command);
                        appendRaw(response.getAllOutput());
                        if (response.getExitCode() != 0 ||
                                !response.getAllOutput().toLowerCase(Locale.ROOT).contains("success")) {
                            throw new IllegalStateException("安装未成功：" + response.getAllOutput().trim());
                        }
                        appendLog("APK 安装成功。");
                        if (deleteAfterInstall) {
                            AdbShellResponse deleteResult = client.shell("rm -f " + shellQuote(remote));
                            if (deleteResult.getExitCode() == 0) appendLog("已删除远程临时 APK：" + remote);
                            else appendLog("远程临时 APK 删除失败：" + deleteResult.getAllOutput().trim());
                        }
                        runUi(new Runnable() {
                            @Override public void run() { showApkProgress(false, "安装成功"); }
                        });
                    } else {
                        runUi(new Runnable() {
                            @Override public void run() { showApkProgress(false, "推送完成：" + remote); }
                        });
                    }
                } catch (Throwable e) {
                    runUi(new Runnable() {
                        @Override public void run() { showApkProgress(false, install ? "推送或安装失败" : "推送失败"); }
                    });
                    fail(install ? "推送或安装失败" : "推送失败", e, false);
                } finally {
                    runUi(new Runnable() {
                        @Override public void run() { setApkButtonsEnabled(true); }
                    });
                }
            }
        });
    }

    private void executeShell(String command) {
        if (command == null || command.trim().length() == 0) {
            toast("请输入 Shell 命令");
            return;
        }
        if (adb == null) {
            toast("请先连接远程设备");
            showPage(PAGE_PAIRING);
            return;
        }
        final String finalCommand = command.trim();
        showPage(PAGE_LOG);
        appendLog("$ " + finalCommand);
        worker.execute(new Runnable() {
            @Override public void run() {
                Kadb client = requireAdb();
                if (client == null) return;
                try {
                    if (client.supportsFeature("shell_v2")) {
                        AdbShellStream stream = null;
                        try {
                            stream = client.openShell(finalCommand);
                            while (true) {
                                AdbShellPacket packet = stream.read();
                                if (packet instanceof AdbShellPacket.StdOut) {
                                    appendRaw(new String(packet.getPayload(), utf8));
                                } else if (packet instanceof AdbShellPacket.StdError) {
                                    appendRaw("[stderr] " + new String(packet.getPayload(), utf8));
                                } else if (packet instanceof AdbShellPacket.Exit) {
                                    byte[] payload = packet.getPayload();
                                    int exit = payload.length == 0 ? -1 : payload[0] & 255;
                                    appendLog("命令结束，退出码：" + exit);
                                    break;
                                }
                            }
                        } finally {
                            if (stream != null) try { stream.close(); } catch (Throwable ignored) { }
                        }
                    } else {
                        appendLog("远程设备不支持 shell_v2；将在命令完成后一次性显示输出。");
                        AdbShellResponse response = client.shell(finalCommand);
                        appendRaw(response.getAllOutput());
                        appendLog("命令结束，退出码：" + response.getExitCode());
                    }
                } catch (Throwable e) {
                    fail("Shell 执行失败", e, false);
                }
            }
        });
    }

    private void refreshDeviceInfo() {
        if (adb == null) {
            toast("请先连接远程设备");
            showPage(PAGE_PAIRING);
            return;
        }
        appendLog("读取设备信息…");
        deviceInfoText.setText("正在读取设备信息…");
        worker.execute(new Runnable() {
            @Override public void run() {
                Kadb client = requireAdb();
                if (client == null) return;
                try {
                    String manufacturer = shellValue(client, "getprop ro.product.manufacturer");
                    String model = shellValue(client, "getprop ro.product.model");
                    String device = shellValue(client, "getprop ro.product.device");
                    String release = shellValue(client, "getprop ro.build.version.release");
                    String sdk = shellValue(client, "getprop ro.build.version.sdk");
                    String fingerprint = shellValue(client, "getprop ro.build.fingerprint");
                    String resolution = client.shell("wm size").getAllOutput().trim();
                    String density = client.shell("wm density").getAllOutput().trim();
                    String battery = parseBattery(client.shell("dumpsys battery").getAllOutput());
                    final String info = s("制造商：", "Manufacturer: ", "製造商：", "Fabricante: ", "メーカー: ", "제조사: ") + blank(manufacturer) + "\n" +
                            s("型号：", "Model: ", "型號：", "Modelo: ", "モデル: ", "모델: ") + blank(model) + "\n" +
                            s("设备代号：", "Device code: ", "裝置代號：", "Código del dispositivo: ", "デバイス コード: ", "기기 코드: ") + blank(device) + "\n" +
                            "Android: " + blank(release) + " (SDK " + (sdk.length() == 0 ? "?" : sdk) + ")\n" +
                            s("屏幕：", "Screen: ", "螢幕：", "Pantalla: ", "画面: ", "화면: ") + (resolution.length() == 0 ? blank("") : resolution.replace("\n", "; ")) + "\n" +
                            s("密度：", "Density: ", "密度：", "Densidad: ", "密度: ", "밀도: ") + (density.length() == 0 ? blank("") : density.replace("\n", "; ")) + "\n" +
                            s("电池：", "Battery: ", "電池：", "Batería: ", "バッテリー: ", "배터리: ") + battery + "\n\nBuild fingerprint:\n" + blank(fingerprint);
                    runUi(new Runnable() {
                        @Override public void run() { deviceInfoText.setText(info); }
                    });
                    appendLog("设备信息读取完成。");
                } catch (Throwable e) {
                    runUi(new Runnable() {
                        @Override public void run() { deviceInfoText.setText("读取失败，请查看日志。"); }
                    });
                    fail("读取设备信息失败", e, false);
                }
            }
        });
    }

    private String shellValue(Kadb client, String command) throws Exception {
        return client.shell(command).getAllOutput().trim();
    }

    private String parseBattery(String raw) {
        String level = field(raw, "level");
        String scaleText = field(raw, "scale");
        int levelInt = parseInt(level, -1);
        int scale = parseInt(scaleText, 100);
        String percent = levelInt < 0 ? "?%" : Math.round(levelInt * 100f / Math.max(1, scale)) + "%";
        String status;
        switch (parseInt(field(raw, "status"), -1)) {
            case 2: status = s("充电中", "Charging", "充電中", "Cargando", "充電中", "충전 중"); break;
            case 3: status = s("放电中", "Discharging", "放電中", "Descargando", "放電中", "방전 중"); break;
            case 4: status = s("未充电", "Not charging", "未充電", "No cargando", "充電していません", "충전 안 함"); break;
            case 5: status = s("已充满", "Full", "已充滿", "Completa", "満充電", "완충"); break;
            default: status = s("未知状态", "Unknown status", "未知狀態", "Estado desconocido", "不明な状態", "알 수 없는 상태");
        }
        String t = field(raw, "temperature");
        String temperature = s("温度未知", "Temperature unknown", "溫度未知", "Temperatura desconocida", "温度不明", "온도 알 수 없음");
        try { temperature = String.format(Locale.getDefault(), "%.1f°C", Double.parseDouble(t) / 10.0); }
        catch (Exception ignored) { }
        String powered = "";
        if ("true".equalsIgnoreCase(field(raw, "AC powered"))) powered = s("，AC 供电", ", AC powered", "，AC 供電", ", alimentación CA", "、AC 電源", ", AC 전원");
        else if ("true".equalsIgnoreCase(field(raw, "USB powered"))) powered = s("，USB 供电", ", USB powered", "，USB 供電", ", alimentación USB", "、USB 電源", ", USB 전원");
        else if ("true".equalsIgnoreCase(field(raw, "Wireless powered"))) powered = s("，无线供电", ", wireless powered", "，無線供電", ", alimentación inalámbrica", "、ワイヤレス給電", ", 무선 전원");
        return percent + s("，", ", ", "，", ", ", "、", ", ") + status + s("，", ", ", "，", ", ", "、", ", ") + temperature + powered;
    }

    private String field(String raw, String key) {
        Pattern p = Pattern.compile("(?im)^\\s*" + Pattern.quote(key) + ":\\s*(.+)$");
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private Kadb requireAdb() {
        Kadb client = adb;
        if (client == null) {
            appendLog("错误：尚未连接远程设备。");
            runUi(new Runnable() {
                @Override public void run() { setConnectionState("未连接", "尚未建立 ADB 通道", false); }
            });
        }
        return client;
    }

    private void fail(final String prefix, Throwable error, final boolean updateStatus) {
        final String message = friendlyError(error);
        appendLog(prefix + "：" + message);
        runUi(new Runnable() {
            @Override public void run() {
                if (updateStatus) setConnectionState(prefix, message, false);
                Toast.makeText(MainActivity.this, prefix + "：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String friendlyError(Throwable error) {
        Throwable rootError = error;
        while (rootError.getCause() != null && rootError.getCause() != rootError) rootError = rootError.getCause();
        String raw = rootError.getMessage() == null ? "" : rootError.getMessage().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("connection refused")) return "连接被拒绝，请核对 IP/端口及无线调试开关";
        if (lower.contains("timed out") || lower.contains("timeout")) return "连接超时，请确认两台设备在同一局域网";
        if (lower.contains("auth")) return "ADB 授权失败，请在远程设备上确认授权或重新配对";
        return raw.length() == 0 ? rootError.getClass().getSimpleName() : raw;
    }

    private void setConnectionState(String title, String detail, boolean connected) {
        statusText.setText(runtimeText(title));
        statusEndpointText.setText(runtimeText(detail));
        statusDot.setTextColor(connected ? ACCENT : DANGER);
        statusText.setTextColor(connected ? ACCENT : TEXT);
    }

    private void showApkProgress(boolean busy, String text) {
        apkProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        apkProgressText.setText(runtimeText(text));
    }

    private void setApkButtonsEnabled(boolean enabled) {
        pushOnlyButton.setEnabled(enabled);
        pushInstallButton.setEnabled(enabled);
        pushOnlyButton.setAlpha(enabled ? 1f : 0.55f);
        pushInstallButton.setAlpha(enabled ? 1f : 0.55f);
    }

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        appendText("[" + time + "] " + runtimeText(message) + "\n");
    }

    private void appendRaw(String text) {
        if (text == null || text.length() == 0) return;
        appendText(text + (text.endsWith("\n") ? "" : "\n"));
    }

    private void appendText(final String text) {
        runUi(new Runnable() {
            @Override public void run() {
                String combined = logText.getText().toString() + text;
                if (combined.length() > MAX_LOG_CHARS) {
                    combined = "[日志过长，已自动截断较早内容]\n" + combined.substring(combined.length() - MAX_LOG_CHARS);
                }
                logText.setText(combined);
                if (!pauseAutoScrollCheck.isChecked()) {
                    logScroll.post(new Runnable() {
                        @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
                    });
                }
            }
        });
    }

    private void copyLogs() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ADB Toolbox Logs", logText.getText()));
        toast("日志已复制");
    }

    private String queryDisplayName(Uri uri) {
        String name = "selected.apk";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) name = cursor.getString(index);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) { }
        }
        return name;
    }

    private void saveInputs() {
        prefs.edit()
                .putString("pairing_ip", pairingIpInput.getText().toString().trim())
                .putString("pairing_connect_port", pairingConnectPortInput.getText().toString().trim())
                .putString("pair_port", pairPortInput.getText().toString().trim())
                .putString("legacy_ip", legacyIpInput.getText().toString().trim())
                .putString("legacy_port", legacyPortInput.getText().toString().trim())
                .putString("remote_path", remotePathInput.getText().toString().trim())
                .putBoolean("delete_remote", deleteRemoteCheck.isChecked())
                .apply();
    }

    private void restoreInputs() {
        pairingIpInput.setText(prefs.getString("pairing_ip", prefs.getString("ip", "")));
        pairingConnectPortInput.setText(prefs.getString("pairing_connect_port", prefs.getString("connect_port", "")));
        pairPortInput.setText(prefs.getString("pair_port", ""));
        legacyIpInput.setText(prefs.getString("legacy_ip", prefs.getString("ip", "")));
        legacyPortInput.setText(prefs.getString("legacy_port", "5555"));
        remotePathInput.setText(prefs.getString("remote_path", "/data/local/tmp/app.apk"));
        deleteRemoteCheck.setChecked(prefs.getBoolean("delete_remote", true));
    }

    private Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535 ? Integer.valueOf(port) : null;
        } catch (Exception e) { return null; }
    }

    /** Returns the active language, preferring the user's explicit selection. */
    private String resolveLanguage() {
        String chosen = prefs.getString("app_language", "system");
        if (!"system".equals(chosen)) return chosen;
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        if ("zh".equals(language)) {
            String country = locale.getCountry();
            return ("TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country)) ? "zh-TW" : "zh-CN";
        }
        if ("en".equals(language) || "es".equals(language) || "ja".equals(language) || "ko".equals(language)) return language;
        return "en";
    }

    /** Localized UI text: Simplified Chinese, English, Traditional Chinese, Spanish, Japanese, Korean. */
    private String s(String zhCn, String en, String zhTw, String es, String ja, String ko) {
        if ("zh-CN".equals(languageCode)) return zhCn;
        if ("zh-TW".equals(languageCode)) return zhTw;
        if ("es".equals(languageCode)) return es;
        if ("ja".equals(languageCode)) return ja;
        if ("ko".equals(languageCode)) return ko;
        return en;
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception e) { return fallback; }
    }

    private boolean validHost(String host) {
        return host != null && host.length() > 0 && host.length() <= 253 && host.matches("[A-Za-z0-9.:-]+");
    }

    private String sanitizeFilename(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.length() > 100) cleaned = cleaned.substring(cleaned.length() - 100);
        return cleaned.length() == 0 ? "selected.apk" : cleaned;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String blank(String value) {
        return value == null || value.trim().length() == 0 ? s("未知", "Unknown", "未知", "Desconocido", "不明", "알 수 없음") : value.trim();
    }

    private String humanBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024));
        if (bytes >= 1024L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private void toast(String message) {
        Toast.makeText(this, runtimeText(message), Toast.LENGTH_SHORT).show();
    }

    /** Converts the remaining operational messages that predate the UI localization. */
    private String runtimeText(String value) {
        if (value == null || "zh-CN".equals(languageCode)) return value;
        String[][] translations = new String[][]{
                {"请输入有效 IP、配对端口和 6 位配对码", "Enter a valid IP address, pairing port, and 6-digit pairing code"},
                {"请输入有效 IP 和连接端口", "Enter a valid IP address and connection port"},
                {"正在配对…", "Pairing…"}, {"开始配对", "Starting pairing"},
                {"配对成功，等待连接", "Paired successfully; waiting to connect"},
                {"请填写无线调试主页面显示的连接端口", "Enter the connection port shown on the Wireless debugging screen"},
                {"配对成功。下一步请输入无线调试主页面显示的连接端口。", "Pairing succeeded. Next, enter the connection port shown on the Wireless debugging screen."},
                {"配对失败", "Pairing failed"}, {"正在连接…", "Connecting…"}, {"连接成功，ADB 通道已就绪。", "connected successfully; the ADB channel is ready."},
                {"连接失败", "Connection failed"}, {"设备未返回连接探测结果", "The device did not return a connection probe result"},
                {"已连接", "Connected"}, {"未连接", "Not connected"}, {"尚未建立 ADB 通道", "No ADB channel established"},
                {"正在断开设备…", "Disconnecting device…"}, {"已断开设备。", "Device disconnected."},
                {"请先连接远程设备", "Connect a remote device first"}, {"尚未连接设备", "No device connected"},
                {"请先选择 APK 文件", "Select an APK file first"}, {"请输入有效的远程绝对路径", "Enter a valid remote absolute path"},
                {"正在读取", "Reading"}, {"读取本机文件：", "Reading local file: "}, {"无法打开所选文件", "Cannot open the selected file"},
                {"所选文件为空", "The selected file is empty"}, {"已选择：", "Selected: "}, {"文件已准备：", "File ready: "},
                {"本机 APK 已准备：", "Local APK ready: "}, {"读取失败", "Read failed"}, {"读取 APK 失败", "Failed to read APK"},
                {"正在推送", "Pushing"}, {"开始 ADB push：", "Starting ADB push: "}, {"push 完成：", "Push complete: "},
                {"推送完成，正在安装…", "Push complete; installing…"}, {"安装未成功：", "Installation was unsuccessful: "},
                {"APK 安装成功。", "APK installed successfully."}, {"已删除远程临时 APK：", "Deleted remote temporary APK: "},
                {"远程临时 APK 删除失败：", "Failed to delete remote temporary APK: "}, {"安装成功", "Installation successful"},
                {"推送完成：", "Push complete: "}, {"推送或安装失败", "Push or installation failed"}, {"推送失败", "Push failed"},
                {"请输入 Shell 命令", "Enter a Shell command"}, {"命令结束，退出码：", "Command finished; exit code: "},
                {"远程设备不支持 shell_v2；将在命令完成后一次性显示输出。", "The remote device does not support shell_v2; output will be shown after completion."},
                {"Shell 执行失败", "Shell execution failed"}, {"读取设备信息…", "Reading device information…"},
                {"设备信息读取完成。", "Device information loaded."}, {"读取失败，请查看日志。", "Read failed; see logs."}, {"读取设备信息失败", "Failed to read device information"},
                {"错误：尚未连接远程设备。", "Error: no remote device connected."}, {"连接被拒绝，请核对 IP/端口及无线调试开关", "Connection refused; check the IP, port, and Wireless debugging setting"},
                {"连接超时，请确认两台设备在同一局域网", "Connection timed out; ensure both devices are on the same LAN"},
                {"ADB 授权失败，请在远程设备上确认授权或重新配对", "ADB authorization failed; confirm authorization on the remote device or pair again"},
                {"日志已复制", "Logs copied"}, {"日志已保存", "Logs saved"}, {"保存日志失败", "Failed to save logs"}
        };
        String result = value;
        for (String[] translation : translations) result = result.replace(translation[0], translation[1]);
        return result;
    }

    private void runUi(final Runnable action) {
        if (destroyed.get()) return;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!destroyed.get() && !isFinishing()) action.run();
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed.set(true);
        Kadb old = adb;
        adb = null;
        worker.shutdownNow();
        if (old != null) {
            try { old.close(); } catch (Throwable ignored) { }
        }
        super.onDestroy();
    }

    // ----- UI helpers -----

    private LinearLayout pageBody() {
        return vertical(dp(16), dp(15), dp(16), dp(24));
    }

    private View pageScroll(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout vertical(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(left, top, right, bottom);
        return layout;
    }

    private TextView sectionTitle(String value) {
        return text(value, 21, TEXT, true);
    }

    private TextView label(String value) {
        return text(value, 14, MUTED, false);
    }

    private TextView help(String value) {
        TextView v = text(value, 13, MUTED, false);
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private EditText input(String hint, boolean number, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(104, 116, 124));
        e.setTextColor(TEXT);
        e.setTextSize(16);
        e.setPadding(dp(14), dp(9), dp(14), dp(9));
        e.setSingleLine(true);
        e.setBackground(panelDrawable(Color.rgb(10, 17, 23), BORDER, 12));
        if (number) {
            int type = InputType.TYPE_CLASS_NUMBER;
            if (password) type |= InputType.TYPE_NUMBER_VARIATION_PASSWORD;
            e.setInputType(type);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        return e;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(panelDrawable(color, color, 10));
        return b;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        return v;
    }

    private GradientDrawable panelDrawable(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void tintCheckBox(CheckBox box) {
        try {
            Method m = android.widget.CompoundButton.class.getMethod("setButtonTintList", ColorStateList.class);
            m.invoke(box, ColorStateList.valueOf(ACCENT));
        } catch (Throwable ignored) { }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int systemDimension(String name) {
        try {
            int id = getResources().getIdentifier(name, "dimen", "android");
            return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        } catch (Throwable ignored) { return 0; }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrapLeft(int left) {
        LinearLayout.LayoutParams p = wrapWrap();
        p.setMargins(left, 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrapTop(int top) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(0, top, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams matchHeightTop(int height, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.setMargins(0, top, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1f);
        p.setMargins(dp(8), 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams logButtonLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(64), dp(42));
        p.setMargins(dp(6), 0, 0, 0);
        return p;
    }
}
