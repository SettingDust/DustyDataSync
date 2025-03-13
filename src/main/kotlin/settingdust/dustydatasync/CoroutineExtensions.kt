package settingdust.dustydatasync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration


/**
 * https://github.com/Kotlin/kotlinx.coroutines/issues/1302#issuecomment-1416493795
 */
fun <T> Flow<T>.chunked(maxSize: Int, interval: Duration) = channelFlow {

    val buffer = mutableListOf<T>()
    var flushJob: Job? = null

    collect { value ->

        flushJob?.cancelAndJoin()
        buffer.add(value)

        if (buffer.size >= maxSize) {
            send(buffer.toList())
            buffer.clear()
        } else {
            flushJob = launch {
                delay(interval)
                if (buffer.isNotEmpty()) {
                    send(buffer.toList())
                    buffer.clear()
                }
            }
        }
    }

    flushJob?.cancelAndJoin()

    if (buffer.isNotEmpty()) {
        send(buffer.toList())
        buffer.clear()
    }
}
