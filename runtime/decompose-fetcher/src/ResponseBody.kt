package tech.annexflow.lvm.decompose.fetcher

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

/** The body of a response that a [Fetcher] read. */
public class ResponseBody(private val response: HttpResponse) {

    /**
     * The body as a value of [typeInfo]. Types other than `String` and byte arrays need the `ContentNegotiation`
     * plugin with a matching converter on the client.
     */
    public suspend fun <T> body(typeInfo: TypeInfo): T = response.body(typeInfo.asKtorTypeInfo())
}

/** The body as a [T]. See [ResponseBody.body]. */
public suspend inline fun <reified T : Any> ResponseBody.body(): T = body(typeInfo<T>())
