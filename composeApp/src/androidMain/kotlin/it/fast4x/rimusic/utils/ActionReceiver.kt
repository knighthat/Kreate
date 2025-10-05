package it.fast4x.rimusic.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class ActionReceiver(private val base: String) : BroadcastReceiver() {
    companion object {
        const val REQUEST_CODE = 100
    }

    class Action internal constructor(
        val value: String,
        val icon: Icon?,
        val title: String?,
        val contentDescription: String?,
        internal val onReceive: (Context, Intent) -> Unit
    ) {
        context(context: Context)
        val pendingIntent: PendingIntent
            get() = PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ REQUEST_CODE,
                /* intent = */ Intent(value).setPackage(context.packageName),
                /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    private val mutableActions = hashMapOf<String, Action>()
    val all get() = mutableActions.toMap()

    val intentFilter
        get() = IntentFilter().apply {
            mutableActions.keys.forEach { addAction(it) }
        }

    internal fun action(
        icon: Icon? = null,
        title: String? = null,
        contentDescription: String? = null,
        onReceive: (Context, Intent) -> Unit
    ) = readOnlyProvider<ActionReceiver, Action> { thisRef, property ->
        val name = "$base.${property.name}"
        val action = Action(
            value = name,
            onReceive = onReceive,
            icon = icon,
            title = title,
            contentDescription = contentDescription
        )

        thisRef.mutableActions += name to action
        { _, _ -> action }
    }

    override fun onReceive(context: Context, intent: Intent) {
        mutableActions[intent.action]?.onReceive?.let { it(context, intent) }
    }

    context(context: Context)
    @JvmName("_register")
    fun register(
        @ContextCompat.RegisterReceiverFlags
        flags: Int = ContextCompat.RECEIVER_NOT_EXPORTED
    ) = register(context, flags)

    fun register(
        context: Context,
        @ContextCompat.RegisterReceiverFlags
        flags: Int = ContextCompat.RECEIVER_NOT_EXPORTED
    ) = ContextCompat.registerReceiver(
        /* context  = */ context,
        /* receiver = */ this@ActionReceiver,
        /* filter   = */ intentFilter,
        /* flags    = */ flags
    )
}

private inline fun <ThisRef, Return> readOnlyProvider(
    crossinline provide: (
        thisRef: ThisRef,
        property: KProperty<*>
    ) -> (thisRef: ThisRef, property: KProperty<*>) -> Return
) = PropertyDelegateProvider<ThisRef, ReadOnlyProperty<ThisRef, Return>> { thissRef, property ->
    val provider = provide(thissRef, property)
    ReadOnlyProperty { innerThisRef, innerProperty -> provider(innerThisRef, innerProperty) }
}