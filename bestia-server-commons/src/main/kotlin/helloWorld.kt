package demo

import java.util.*

class KotlinGreetingJoiner(
    val greeter: String
) {

    val names = ArrayList<String?>()

    fun addName(name: String?) {
        names.add(name)
    }

    fun getJoinedGreeting(): String {
        return "$greeter Test"
    }
}
