package com.kanawish.permission

import com.kanawish.di.ActivitySingleton
import javax.inject.Inject

@ActivitySingleton
class PermissionManager @Inject constructor (val activity) {
    hasPermission
}