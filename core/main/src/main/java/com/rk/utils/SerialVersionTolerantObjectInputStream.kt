package com.rk.utils

import java.io.Externalizable
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * An [ObjectInputStream] that keeps reading the app's own classes after their `serialVersionUID` changed, as long as
 * their serialized fields did not.
 *
 * A class that does not declare a `serialVersionUID` gets one computed from its name, interfaces, fields, constructors
 * and non-private methods, so adding a method to it, or to an interface it implements, changes the value. A plain
 * [ObjectInputStream] then rejects everything an older version wrote with an [java.io.InvalidClassException], although
 * the stored fields would still fit. The same happens once when a class starts to declare a `serialVersionUID`.
 *
 * This stream reads such data with the local class descriptor instead, but only where that is safe: the class belongs
 * to the app (`com.rk.`), still exists, uses the default serialized form (neither `readObject`/`writeObject` nor
 * [Externalizable]), and the stream lists the same serializable fields, with the same types and in the same order, as
 * the local class. The instance data in the stream is then laid out exactly the way the local class reads it. Any other
 * mismatch is still rejected as usual.
 */
class SerialVersionTolerantObjectInputStream(input: InputStream) : ObjectInputStream(input) {

    override fun readClassDescriptor(): ObjectStreamClass {
        val streamDescriptor = super.readClassDescriptor()
        if (!streamDescriptor.name.startsWith(TOLERATED_PACKAGE_PREFIX)) return streamDescriptor

        val localClass =
            try {
                resolveClass(streamDescriptor)
            } catch (_: ClassNotFoundException) {
                return streamDescriptor
            }
        val localDescriptor = ObjectStreamClass.lookup(localClass) ?: return streamDescriptor

        val tolerated =
            localDescriptor.serialVersionUID != streamDescriptor.serialVersionUID &&
                localClass.usesDefaultSerializedForm() &&
                hasSameFields(localDescriptor, streamDescriptor)

        return if (tolerated) localDescriptor else streamDescriptor
    }

    private fun Class<*>.usesDefaultSerializedForm(): Boolean =
        !Externalizable::class.java.isAssignableFrom(this) &&
            declaredMethods.none { it.name == "readObject" || it.name == "writeObject" }

    private fun hasSameFields(local: ObjectStreamClass, stream: ObjectStreamClass): Boolean {
        val localFields = local.fields
        val streamFields = stream.fields
        return localFields.size == streamFields.size &&
            localFields.zip(streamFields).all { (localField, streamField) ->
                localField.name == streamField.name &&
                    localField.typeCode == streamField.typeCode &&
                    localField.typeString == streamField.typeString
            }
    }

    private companion object {
        const val TOLERATED_PACKAGE_PREFIX = "com.rk."
    }
}
