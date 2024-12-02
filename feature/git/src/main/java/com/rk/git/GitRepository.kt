package com.rk.git

import java.io.File

data class GitRepository(val rootPath:File){
    data class GitRemote(val url:String,val name:String)
    data class Commit(val commitHash:String)
    data class Branch(var head:String)
    
    
    private val remotes = mutableMapOf<String,GitRemote>()
    private val branchs = mutableMapOf<String,Branch>()
    
    suspend fun addRemote(remote:GitRemote){
        remotes[remote.name] = remote
    }
    
    suspend fun removeRemote(remote: GitRemote){
        remotes.remove(remote.name)
    }
    
    
    suspend fun push(){
    
    }
    
    suspend fun pull(){
    
    }
    
    suspend fun fetch(){
    
    }
    
    suspend fun add(file: File){
    
    }
    
    suspend fun commit(name: String){
    
    }
    
    suspend fun reset(commit: Commit,hard:Boolean = false){
    
    }
    
    suspend fun clean(){
    
    }
    
    suspend fun mergeBranchToCurrentBranch(branch: Branch){
    
    }
    
    suspend fun getCurrentBranch():Branch{
        TODO()
    }
    
    suspend fun getCurrentHead():Commit{
        TODO()
    }
    
    suspend fun isFileTracked(file: File):Boolean{
        TODO()
    }
    
}
