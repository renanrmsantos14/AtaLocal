package br.com.betinhos.atalocal.settings

data class RetentionPolicy(val days: Int?) {
    init { require(days == null || days > 0) { "A retenção deve ser positiva ou desativada" } }

    /** Recebe dias absolutos, não milissegundos; a conversão fica na camada de dados. */
    fun shouldDelete(now: Long, createdAt: Long): Boolean {
        val limit = days ?: return false
        if (now < createdAt) return false
        return now - createdAt > limit
    }
}
