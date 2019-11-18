@file:Suppress("unused")

package nu.westlin.arrowlab

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import arrow.fx.IO
import arrow.fx.handleError
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class Person(val name: String, val age: Int) {
    @JsonIgnore
    fun isChild() = age < 18

    @JsonIgnore
    fun isAdult() = age >= 18
}

val homer = Person("Homer", 36)
val marge = Person("Marge", 34)
val bart = Person("Bart", 10)
val lisa = Person("Lisa", 8)
val maggie = Person("Maggie", 1)
val simpsons = listOf(homer, marge, bart, lisa, maggie)

val peter = Person("Peter", 40)
val lois = Person("Lois", 38)
val meg = Person("Meg", 17)
val chris = Person("Chris", 15)
val stewie = Person("Stewie", 2)
val familyGuys = listOf(peter, lois, meg, chris, stewie)

data class HttpResponse(val body: String, val httpStatus: Int) {
    fun isSuccessful() = httpStatus in (200..299)
    fun isError() = httpStatus >= 300
}


class HttpPersonService(private val mapper: ObjectMapper) {

    fun getAll(): HttpResponse {
        return HttpResponse(mapper.writeValueAsString(simpsons + familyGuys), 200)
    }
}

sealed class PersonError {
    object NotFoundError : PersonError()
    object UnknownServerError : PersonError()
}

fun getAllPersonsDataSource(service: HttpPersonService, mapper: ObjectMapper): IO<Either<PersonError, List<Person>>> {
    return IO {
        val response = service.getAll()
        when (response.httpStatus) {
            200 -> Right(mapper.readValue<List<Person>>(response.body))
            404 -> Left(PersonError.NotFoundError)
            else -> Left(PersonError.UnknownServerError)
        }
    }.handleError { Left(PersonError.UnknownServerError) }
}

class AdultsView {
    fun showNotFoundError() {
        println("Could not find any persons")
    }

    fun showUnknownServerError() {
        println("Got an UnknownServerError")
    }

    fun showAdults(adults: List<Person>) {
        println("Adults:\n${adults.joinToString(separator = "\n") { "\t$it" }}")
    }

}

fun getAdultsUseCase(service: HttpPersonService, mapper: ObjectMapper): IO<Either<PersonError, List<Person>>> {
    return getAllPersonsDataSource(service, mapper).map { maybePersons ->
        maybePersons.flatMap { heroes ->
            Right(heroes.filter { person -> person.isAdult() })
        }
    }
}

fun adultsPresentation(view: AdultsView, service: HttpPersonService, mapper: ObjectMapper) {
    getAdultsUseCase(service, mapper).unsafeRunAsync {
        it.map { maybeAdults ->
            maybeAdults.fold(
                { error ->
                    when (error) {
                        is PersonError.NotFoundError -> view.showNotFoundError()
                        is PersonError.UnknownServerError -> view.showUnknownServerError()
                    }
                },
                { adults -> view.showAdults(adults) }
            )
        }
    }
}

fun main() {
    val mapper = jacksonObjectMapper()
    adultsPresentation(AdultsView(), HttpPersonService(mapper), mapper)
}