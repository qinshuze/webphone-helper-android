package com.ccps.remotedesktop.service.message

class ClientEvent {
    fun interface Closed {
        fun invoke(code: Int, reason: String): Unit
    }

    fun interface Closing {
        fun invoke(code: Int, reason: String): Unit
    }

    fun interface Message {
        fun invoke(message: Client.ReceiveMessage): Unit
    }

    fun interface Open {
        fun invoke(): Unit
    }

    fun interface Failure {
        fun invoke(): Unit
    }
}