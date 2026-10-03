package android.os

open class AndroidException : Exception {
    constructor() : super()
    constructor(name: String?) : super(name)
    constructor(name: String?, cause: Throwable?) : super(name, cause)
    constructor(cause: Exception?) : super(cause)
}

open class RemoteException : AndroidException {
    constructor() : super()
    constructor(message: String?) : super(message)
}

open class TransactionTooLargeException : RemoteException {
    constructor() : super()
    constructor(msg: String?) : super(msg)
}
