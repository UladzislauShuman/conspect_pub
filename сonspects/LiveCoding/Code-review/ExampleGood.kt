import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull
import java.math.BigDecimal

// о хорошем

@Service
// через конструктор
class RewardService(
    private val userRepository: UserRepository,
    private val paymentClient: PaymentClient
) {
    // логгер
    private val log = LoggerFactory.getLogger(javaClass)

    // уже без транзакций
    fun payRewards(rawIds: List<Any>) {
        // отсеиваем и кастим в Long
        val validUserIds = rawIds.mapNotNull { it as? Long }

        // батчим
        validUserIds.chunked(1000).forEach { batch ->
            processBatch(batch)
        }
    }

    private fun processBatch(userIds: List<Long>) {
        // всех за 1 раз достаем, а не за N+1
        val users = userRepository.findAllById(userIds)

        for (user in users) {
            try {
                // меньше if
                if (user.status != UserStatus.ACTIVE) continue

                // BigDecimal для денег
                var bonusAmount = BigDecimal("500.00")
                if (user.tier == UserTier.VIP) {
                    bonusAmount = bonusAmount.multiply(BigDecimal("1.50"))
                }

                // вызываем клиент
                paymentClient.sendMoney(user.accountId, bonusAmount)
                log.info("User rewarded: {}", user.id) // логгируем

            } catch (e: PaymentException) { // конкретное исключение
                log.error("Failed to pay reward to user: ${user.id}", e)
            }
        }
    }
}