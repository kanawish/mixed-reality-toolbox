package com.kanawish.di

import toothpick.config.Binding
import toothpick.config.Module

inline fun <reified T> Module.bind(): Binding<T> = bind(T::class.java)
