package com.rk.xededitor.MainActivity.network

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

class SFTPClient {
  
  lateinit var session: Session
  var isConnected = false
  
  fun connect(username: String, password: String, ip: String, port: Int){
    val jsch = JSch()
    session = jsch.getSession(username, ip, port)
    session.setPassword(password)
    session.setConfig("StrictHostKeyChecking", "no")
    session.connect()
    isConnected = session.isConnected
    this.session = session
  }
  
  fun listFiles() {
    //move to constructor
    val channel = session.openChannel("sftp") as ChannelSftp
    channel.connect()
    
    val files = channel.ls(".")
    for (file in files) {
      println(file.filename)
    }
    channel.disconnect()
  }
}