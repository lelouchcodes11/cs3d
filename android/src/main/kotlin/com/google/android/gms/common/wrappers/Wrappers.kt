package com.google.android.gms.common.wrappers

import android.content.Context
import android.content.pm.PackageInfo

open class PackageManagerWrapper(private val context: Context) {
    open fun getPackageInfo(packageName: String, flags: Int): PackageInfo =
        context.getPackageManager().getPackageInfo(packageName, flags)
}

object Wrappers {
    @JvmStatic
    fun packageManager(context: Context): PackageManagerWrapper = PackageManagerWrapper(context)
}
