-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.script.ScriptEngineFactory
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

-keep class org.eclipse.jgit.util.SystemReader { *; }
-keep class org.eclipse.jgit.storage.file.FileBasedConfig { *; }
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.**
-dontwarn java.awt.BorderLayout
-dontwarn java.awt.Color
-dontwarn java.awt.Component
-dontwarn java.awt.Container
-dontwarn java.awt.Cursor
-dontwarn java.awt.Dimension
-dontwarn java.awt.Font
-dontwarn java.awt.Frame
-dontwarn java.awt.Graphics
-dontwarn java.awt.IllegalComponentStateException
-dontwarn java.awt.Image
-dontwarn java.awt.Insets
-dontwarn java.awt.Label
-dontwarn java.awt.LayoutManager
-dontwarn java.awt.MediaTracker
-dontwarn java.awt.TextArea
-dontwarn java.awt.Toolkit
-dontwarn java.awt.Window
-dontwarn java.awt.event.ActionEvent
-dontwarn java.awt.event.ActionListener
-dontwarn java.awt.event.KeyEvent
-dontwarn java.awt.event.KeyListener
-dontwarn java.awt.event.MouseEvent
-dontwarn java.awt.event.MouseListener
-dontwarn java.awt.event.WindowAdapter
-dontwarn java.awt.event.WindowEvent
-dontwarn java.awt.event.WindowListener
-dontwarn java.awt.image.ImageObserver
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.script.AbstractScriptEngine
-dontwarn javax.script.Bindings
-dontwarn javax.script.Compilable
-dontwarn javax.script.CompiledScript
-dontwarn javax.script.Invocable
-dontwarn javax.script.ScriptContext
-dontwarn javax.script.ScriptEngine
-dontwarn javax.script.ScriptException
-dontwarn javax.script.SimpleBindings
-dontwarn javax.security.auth.login.LoginContext
-dontwarn javax.servlet.ServletException
-dontwarn javax.servlet.http.HttpServlet
-dontwarn javax.servlet.http.HttpServletRequest
-dontwarn javax.servlet.http.HttpServletResponse
-dontwarn javax.swing.BorderFactory
-dontwarn javax.swing.Icon
-dontwarn javax.swing.JComponent
-dontwarn javax.swing.JFrame
-dontwarn javax.swing.JInternalFrame
-dontwarn javax.swing.JLabel
-dontwarn javax.swing.JList
-dontwarn javax.swing.JMenuItem
-dontwarn javax.swing.JPanel
-dontwarn javax.swing.JPopupMenu
-dontwarn javax.swing.JScrollPane
-dontwarn javax.swing.JSplitPane
-dontwarn javax.swing.JTextArea
-dontwarn javax.swing.JTextPane
-dontwarn javax.swing.JTree
-dontwarn javax.swing.JWindow
-dontwarn javax.swing.SwingUtilities
-dontwarn javax.swing.UIManager
-dontwarn javax.swing.border.Border
-dontwarn javax.swing.border.MatteBorder
-dontwarn javax.swing.event.ListSelectionEvent
-dontwarn javax.swing.event.ListSelectionListener
-dontwarn javax.swing.event.TreeSelectionEvent
-dontwarn javax.swing.event.TreeSelectionListener
-dontwarn javax.swing.plaf.SplitPaneUI
-dontwarn javax.swing.plaf.basic.BasicSplitPaneDivider
-dontwarn javax.swing.plaf.basic.BasicSplitPaneUI
-dontwarn javax.swing.text.AttributeSet
-dontwarn javax.swing.text.BadLocationException
-dontwarn javax.swing.text.DefaultStyledDocument
-dontwarn javax.swing.text.Document
-dontwarn javax.swing.text.MutableAttributeSet
-dontwarn javax.swing.text.SimpleAttributeSet
-dontwarn javax.swing.text.StyleConstants
-dontwarn javax.swing.text.StyledDocument
-dontwarn javax.swing.tree.DefaultMutableTreeNode
-dontwarn javax.swing.tree.DefaultTreeModel
-dontwarn javax.swing.tree.MutableTreeNode
-dontwarn javax.swing.tree.TreeModel
-dontwarn javax.swing.tree.TreeNode
-dontwarn javax.swing.tree.TreePath
-dontwarn kotlin.Cloneable$DefaultImpls
-dontwarn org.ietf.jgss.MessageProp
-dontwarn sun.security.x509.X509Keyrohit
# Preserve all fields for classes serialized/deserialized by Gson
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * extends com.google.gson.TypeAdapterFactory { *; }
-keep class * extends com.google.gson.JsonSerializer { *; }
-keep class * extends com.google.gson.JsonDeserializer { *; }
-keep class * extends com.google.gson.JsonElement { *; }
# Keep fields with @SerializedName annotation (used for serialization/deserialization)
-keepattributes *Annotation*
# Keep classes used by Gson for reflection-based deserialization
-keep class ** extends com.google.gson.reflect.TypeToken { *; }
# Keep generic signatures; needed for correct type resolution
-keepattributes Signature
# Keep class TypeToken (respectively its generic signature)
-keep class com.google.gson.reflect.TypeToken { *; }
# Keep any (anonymous) classes extending TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class org.joni.ast.QuantifierNode { *; }
-keep class com.rk.plugin.server.Manifest.** { *; }
# For using GSON @Expose annotation
-keepattributes *Annotation*
# Gson specific classes
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Prevent R8 from leaving Data object members always null
-keepclasseswithmembers class * {
    <init>(...);
    @com.google.gson.annotations.SerializedName <fields>;
}
# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep class com.rk.App { *; }
# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.rk.plugin.server.Manifest { *; }
# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
# Keep the no-args constructor of the deserialized class
-keepclassmembers class com.rk.plugin.server.Manifest {
  <init>();
}
-keep,allowobfuscation,allowoptimization class com.rk.plugin.server.Manifest {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.rk.plugin.server.Manifest {
   <fields>;
}
-keepclassmembers class com.rk.plugin.server.Manifest {
 !transient <fields>;
}
# Keep classes and members for all models used with Gson
#-keep class com.rk.xededitor.** { *; }
#-keep class com.rk.plugin.** { *; }
# Keep all classes that might be used by Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep all classes with a no-arg constructor
-keepclassmembers class * {
    public <init>();
}
# Keep all classes that are referenced by your Manifest class
-keep class * extends l6.g { *; }
# If l6.g is an interface, use this instead:
# -keep interface l6.g { *; }
# -keep class * implements l6.g { *; }
# Explicitly keep the class mentioned in the error
-keep class l6.g { *; }
# If Gson is dynamically creating adapters, we need to keep all TypeAdapterFactory implementations
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory {
    public <init>();
}
# If you're using any custom TypeAdapter, make sure to keep those as well
-keep class * extends com.google.gson.TypeAdapter { *; }


-keepclasseswithmembernames class com.rk.plugin.server.api.API {*;}
-keepclasseswithmembernames class com.rk.plugin.server.api.PluginLifeCycle  {*;}
-keepclasseswithmembernames class com.rk.plugin.server.** {*;}
-keep class com.rk.xededitor.MainActivity.MainActivity {*;}
-keepclasseswithmembernames class com.rk.App {*;}
-keepclasseswithmembernames class com.rk.xededitor.BaseActivity {*;}

-keepclassmembernames class com.rk.plugin.server.api.API {*;}
-keepclassmembernames class com.rk.plugin.server.api.PluginLifeCycle {*;}
-keepclassmembernames class com.rk.plugin.server.** {*;}
-keepclassmembernames class com.rk.App {*;}
-keepclassmembernames class com.rk.xededitor.BaseActivity {*;}

-keepnames class com.rk.plugin.server.api.API {*;}
-keepnames class com.rk.plugin.server.api.PluginLifeCycle {*;}
-keepnames class com.rk.plugin.server.** {*;}
-keepnames class com.rk.App {*;}
-keepnames class com.rk.xededitor.BaseActivity {*;}

-dontwarn sun.security.x509.X509Key
-dontobfuscate
-dontshrink
-keepattributes *Annotation*


