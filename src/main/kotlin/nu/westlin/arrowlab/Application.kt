@file:Suppress("unused")

package nu.westlin.arrowlab

import arrow.core.*
import arrow.core.extensions.id.comonad.extract
import arrow.mtl.Reader
import arrow.mtl.ReaderApi
import arrow.mtl.flatMap
import arrow.mtl.map
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

data class DependencyGraph(val view: AdultsView, val service: HttpPersonService, val mapper: ObjectMapper)

data class HttpResponse(val body: String, val httpStatus: Int) {
    fun isSuccessful() = httpStatus in (200..299)
    fun isError() = httpStatus >= 300
}

interface HttpPersonService {
    fun getAll(): HttpResponse
}

class HttpPersonServiceHappyImpl(private val mapper: ObjectMapper) : HttpPersonService {

    override fun getAll(): HttpResponse {
        return HttpResponse(mapper.writeValueAsString(simpsons + familyGuys), 200)
    }
}

class HttpPersonServiceErrorImpl(private val mapper: ObjectMapper) : HttpPersonService {

    override fun getAll(): HttpResponse = HttpResponse("", httpStatus = 500)
}

sealed class PersonError {
    object NotFoundError : PersonError()
    object UnknownServerError : PersonError()
}

fun getAllPersonsDataSource(): Reader<DependencyGraph, IO<Either<PersonError, List<Person>>>> {
    return ReaderApi.ask<DependencyGraph>().map { ctx ->
        IO {
            val response = ctx.service.getAll()
            when (response.httpStatus) {
                200 -> Right(ctx.mapper.readValue<List<Person>>(response.body))
                404 -> Left(PersonError.NotFoundError)
                else -> Left(PersonError.UnknownServerError)
            }
        }.handleError { Left(PersonError.UnknownServerError) }
    }
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

fun getAdultsUseCase(): Reader<DependencyGraph, IO<Either<PersonError, List<Person>>>> {
    return getAllPersonsDataSource().map { io ->
        io.map { maybePersons ->
            maybePersons.flatMap { heroes ->
                Right(heroes.filter { person -> person.isAdult() })
            }
        }
    }
}

fun adultsPresentation(): Reader<DependencyGraph, IO<Unit>> {
    return ReaderApi.ask<DependencyGraph>().flatMap { ctx ->
        getAdultsUseCase().map { io ->
            io.map { maybeAdults ->
                maybeAdults.fold(
                    { error ->
                        when (error) {
                            is PersonError.NotFoundError -> ctx.view.showNotFoundError()
                            is PersonError.UnknownServerError -> ctx.view.showUnknownServerError()
                        }
                    },
                    { adults -> ctx.view.showAdults(adults) }
                )
            }
        }
    }
}

fun main() {
    val mapper = jacksonObjectMapper()
    println("Happy case:")
    val adultsPresentation = adultsPresentation()

    adultsPresentation.run(DependencyGraph(AdultsView(), HttpPersonServiceHappyImpl(mapper), mapper)).extract().unsafeRunAsync { }

    println()
    println("Not so happy case:")
    adultsPresentation.run(DependencyGraph(AdultsView(), HttpPersonServiceErrorImpl(mapper), mapper)).fix().extract().unsafeRunAsync { }
}