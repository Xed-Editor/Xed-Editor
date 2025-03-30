import com.rk.controlpanel.showControlPanel
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity

//just a class to prevent use of MainActivity.withContext where its not necessary
object CheapEventBus {
    fun showControlPanel(){
        MainActivity.withContext { this.showControlPanel() }
    }
}