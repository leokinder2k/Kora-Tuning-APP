package com.leokinder2k.koratuningcompanion.navigation

internal object NavigationTabSettings {
    fun parseOrder(savedNames: String, defaultNames: List<String>): List<String> {
        val saved = savedNames
            .split(',')
            .map { it.trim() }
            .filter { it in defaultNames }
            .distinct()
        return saved + defaultNames.filterNot { it in saved }
    }

    fun parseVisible(orderNames: List<String>, savedNames: String): List<String> {
        val visibleNames = savedNames
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (visibleNames.isEmpty()) {
            return orderNames
        }
        return orderNames.filter { it in visibleNames }.ifEmpty { orderNames.take(1) }
    }

    fun visibleAfterToggle(
        orderNames: List<String>,
        currentVisibleNames: List<String>,
        toggledName: String,
        isVisible: Boolean
    ): List<String> {
        val currentVisible = currentVisibleNames.filter { it in orderNames }.distinct()
        val updated = if (isVisible) {
            currentVisible + toggledName
        } else {
            currentVisible.filterNot { it == toggledName }
        }
        return orderBy(updated, orderNames).ifEmpty { currentVisible }
    }

    fun orderBy(visibleNames: List<String>, orderNames: List<String>): List<String> {
        val visibleSet = visibleNames.toSet()
        return orderNames.filter { it in visibleSet }
    }

    fun selectedAfterVisibilityChange(selectedName: String, visibleNames: List<String>): String {
        return if (selectedName in visibleNames) {
            selectedName
        } else {
            visibleNames.firstOrNull() ?: selectedName
        }
    }

    fun moveBy(orderNames: List<String>, name: String, delta: Int): List<String> {
        val currentIndex = orderNames.indexOf(name)
        if (currentIndex < 0) {
            return orderNames
        }
        val targetIndex = (currentIndex + delta).coerceIn(orderNames.indices)
        if (targetIndex == currentIndex) {
            return orderNames
        }
        return orderNames.toMutableList().apply {
            removeAt(currentIndex)
            add(targetIndex, name)
        }
    }
}
