import com.rk.libcommons.toast
import com.rk.resources.strings

//just a class to prevent use of MainActivity.withContext where its not necessary
object CheapEventBus {
    fun showControlPanel(){
        toast(strings.ni)
    }
}