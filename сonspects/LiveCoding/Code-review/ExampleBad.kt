import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Service
class RewardService {

    @Autowired
    lateinit var userRepository: UserRepository // field injection

    // состояние в Singleton 
    // и в любом случае -- не потокобезопасно 
    var successfulPayouts: Int = 0

    // тут идет HTTP вызов и работа с БД
    // это две долгие операции
    @Transactional
    // надеюсь список не огромный приходит
    // да и в любом случае -- придет много, это мы еще дольше...
    fun payRewards(userIds: List<Any>) {
        // почему не сделать Bean-ом
        // ну и также -- он не прокси, значит и магии Spring нет
        val paymentClient = PaymentClient()

        for (id in userIds) {
            try {
                // явный cast
                // не обрабатывается никак (нам важно каждое пришедшее? может стоит залоггировать?)
                val userId = id as Long
                
                // в цикле берем по одному пользователю. N + 1
                // еще Optional get. может быть NPE
                val user = userRepository.findById(userId).get()

                // много if
                if (user != null) {
                    if (user.status == "ACTIVE") {
                        // double для денег 
                        // магическое число
                        var bonusAmount = 500.0

                        if (user.tier == "VIP") {
                            // магическое число
                            bonusAmount = bonusAmount * 1.5
                        }

                        paymentClient.sendMoney(user.accountId, bonusAmount)
                        // вот поэтому не потокобезопасно
                        successfulPayouts++
                        // не логгер
                        System.out.println("User rewarded: " + user.name)
                    }
                }
            } catch (e: Exception) { // общее исключение
                System.out.println("Error processing user: " + id)
            }
        }
    }
}

// просто для контекста тут есть 
interface UserRepository {
    fun findById(id: Long): java.util.Optional<User>
}
class PaymentClient {
    fun sendMoney(accountId: String, amount: Double) {}
}
class User(val id: Long, val name: String, val status: String, val tier: String, val accountId: String)