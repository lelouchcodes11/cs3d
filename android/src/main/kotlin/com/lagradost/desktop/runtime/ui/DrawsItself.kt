package com.lagradost.desktop.runtime.ui

/**
 * Compat framework/androidx views that have no dedicated renderer node and paint in onDraw (the
 * renderer calls onDraw for app classes only, unless the view is marked with this interface).
 */
interface DrawsItself
