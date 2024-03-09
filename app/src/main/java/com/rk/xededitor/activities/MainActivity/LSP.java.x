package com.rk.xededitor.activities.MainActivity;

// import static io.github.rosemoe.sora.app.Utilslua.switchThemeIfRequired;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.rk.xededitor.rkUtils;
import com.tang.vscode.LuaLanguageClient;
import com.tang.vscode.LuaLanguageServer;

import org.eclipse.lsp4j.jsonrpc.Launcher;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.dsl.LanguageDefinitionListBuilder;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider;
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider;
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition;
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler;
import io.github.rosemoe.sora.lsp.editor.LspEditor;
import io.github.rosemoe.sora.lsp.editor.LspProject;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;

import kotlin.Unit;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LSP extends AppCompatActivity {
  private volatile LspEditor lspEditor;
  private CodeEditor editor;
  private volatile LspProject lspProject;
  private Menu rootMenu;

  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    editor = new CodeEditor(this);
    setContentView(editor);

    

    try {
      ensureTextmateTheme();
      // switchThemeIfRequired(this, editor);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ForkJoinPool.commonPool()
        .execute(
            () -> {
              try {
                unAssets();
                
                setEditorText();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              connectToLanguageServer();
            });
  }

  private void setEditorText() throws IOException {
    var file = new File(getExternalCacheDir(), "testProject/sample.lua");

    var text = ContentIO.createFrom(new FileInputStream(file));

    runOnUiThread(() -> editor.setText(text, null));
  }

  private void connectToLanguageServer() {
    runOnUiThread(
        () -> {
          toast("(Java Activity) Starting Language Server...");
          editor.setEditable(false);
        });
    
    
    

    var projectPath = new File(getExternalCacheDir(), "testProject").getAbsolutePath();

    var intent = new Intent(this, LspLanguageServerService.class);

      int port = 8282;
    
      intent.putExtra("port", port);
   
    

    startService(intent);

 

    lspProject = new LspProject(projectPath);

    

    final Object lock = new Object();

    runOnUiThread(
        () -> {
          lspEditor = lspProject.createEditor("$projectPath/sample.lua");

          var wrapperLanguage = createTextMateLanguage();
          lspEditor.setWrapperLanguage(wrapperLanguage);
          lspEditor.setEditor(editor);

          synchronized (lock) {
            lock.notify();
          }
        });

    synchronized (lock) {
      try{
        lock.wait();
      }catch (Exception e){
        e.printStackTrace();
      }
      
    }

    boolean connected;

    // delay(Timeout[Timeouts.INIT].toLong()) //wait for server start

    try {
      lspEditor.connectWithTimeoutBlocking();

      var changeWorkspaceFoldersParams = new DidChangeWorkspaceFoldersParams();

      changeWorkspaceFoldersParams.setEvent(new WorkspaceFoldersChangeEvent());

      changeWorkspaceFoldersParams
          .getEvent()
          .setAdded(List.of(new WorkspaceFolder("file://$projectPath/std/Lua53", "MyLuaProject")));

      Objects.requireNonNull(lspEditor.getRequestManager())
          .didChangeWorkspaceFolders(changeWorkspaceFoldersParams);

      connected = true;

    } catch (Exception e) {
      connected = false;
      e.printStackTrace();
    }

    boolean finalConnected = connected;

    runOnUiThread(
        () -> {
          if (finalConnected) {
            toast("Initialized Language server");
          } else {
            toast("Unable to connect language server");
          }
          editor.setEditable(true);
        });
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  

  private TextMateLanguage createTextMateLanguage() {

    var builder = new LanguageDefinitionListBuilder();

    builder.language(
        "lua",
        languageDefinitionBuilder -> {
          languageDefinitionBuilder.setGrammar("textmate/lua/syntaxes/lua.tmLanguage.json");
          languageDefinitionBuilder.setScopeName("source.lua");
          languageDefinitionBuilder.setLanguageConfiguration(
              "textmate/lua/language-configuration.json");
          return Unit.INSTANCE;
        });

    GrammarRegistry.getInstance().loadGrammars(builder.build());

    return TextMateLanguage.create("source.lua", false);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  protected void onDestroy() {
    super.onDestroy();

    editor.release();
    try {
      ForkJoinPool.commonPool()
          .execute(
              () -> {
                lspEditor.dispose();
                lspProject.dispose();
              });
      stopService(new Intent(this, LspLanguageServerService.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void unAssets() throws IOException {
    ZipFile zipFile = new ZipFile(getPackageResourcePath());
    Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
    while (zipEntries.hasMoreElements()) {
      ZipEntry zipEntry = zipEntries.nextElement();
      String fileName = zipEntry.getName();
      if (fileName.startsWith("assets/testProject/")) {
        InputStream inputStream = zipFile.getInputStream(zipEntry);
        // The compiler will be optimized here, don't worry
        File filePath = new File(getExternalCacheDir(), fileName.substring("assets/".length()));
        filePath.getParentFile().mkdirs();
        FileOutputStream outputStream = new FileOutputStream(filePath);

        byte[] buffer = new byte[1024];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, len);
        }

        inputStream.close();
        outputStream.close();
      }
    }
    zipFile.close();
  }

  private void ensureTextmateTheme() throws Exception {

    var editorColorScheme = editor.getColorScheme();

    if (editorColorScheme instanceof TextMateColorScheme) {
      return;
    }

    FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getAssets()));

    var themeRegistry = ThemeRegistry.getInstance();

    var path = "textmate/quietlight.json";

    themeRegistry.loadTheme(
        new ThemeModel(
            IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
            "quitelight"));

    themeRegistry.setTheme("quietlight");

    editorColorScheme = TextMateColorScheme.create(themeRegistry);

    editor.setColorScheme(editorColorScheme);
  }

  class EventListener implements EventHandler.EventListener {
    private WeakReference<LSP> ref;

    public EventListener(LSP activity) {
      this.ref = new WeakReference<>(activity);
    }

    @Override
    public void initialize(@Nullable LanguageServer server, @NonNull InitializeResult result) {

      var activity = ref.get();

      if (activity == null) {
        return;
      }

   /*   activity.runOnUiThread(
          () -> {
            var item = rootMenu.findItem(R.id.code_format);

            var isEnabled = result.getCapabilities().getDocumentFormattingProvider() != null;

            item.setEnabled(isEnabled);
          });*/
    }

    @Override
    public void onHandlerException(@NonNull Exception exception) {}

    @Override
    public void onShowMessage(@Nullable MessageParams messageParams) {}

    @Override
    public void onLogMessage(@Nullable MessageParams messageParams) {}
  }

  class LspLanguageServerService extends Service {

    private static final String TAG = "LanguageServer";

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      // Only used in test
      Executors.newSingleThreadExecutor()
          .execute(
              () -> {
                int port = 0;
                if (intent != null) {
                  port = intent.getIntExtra("port", 0);
                }

                try (ServerSocket socket = new ServerSocket(port)) {
                  Log.d(TAG, "Starting socket on port " + socket.getLocalPort());
                  var socketClient = socket.accept();
                  Log.d(TAG, "Connected to the client on port " + socketClient.getPort());

                  try {
                    LuaLanguageServer server = new LuaLanguageServer();

                    var inputStream = socketClient.getInputStream();
                    var outputStream = socketClient.getOutputStream();

                    Launcher<LuaLanguageClient> launcher =
                        Launcher.createLauncher(
                            server, LuaLanguageClient.class, inputStream, outputStream);

                    var remoteProxy = launcher.getRemoteProxy();
                    server.connect(remoteProxy);

                    launcher.startListening().get();
                  } catch (InterruptedException | ExecutionException e) {
                    Log.d(TAG, "Unexpected exception is thrown in the Language Server Thread.", e);
                  } finally {
                    socketClient.close();
                  }
                } catch (IOException e) {
                  Log.d(TAG, "Exception when setting up server socket.", e);
                }
              });

      return START_STICKY;
    }
  }
}
