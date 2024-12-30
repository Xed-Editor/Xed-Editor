/**
 * Copyright (c) 2022 Sebastian Thomschke and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.tm4e.core.internal.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public final class ObjectCloner {

	private static final WeakHashMap<Class<?>, Optional<Method>> CLONE_METHODS_CACHE = new WeakHashMap<>();

	public static <@NonNull T> T deepClone(final T obj) {
		return deepClone(obj, new IdentityHashMap<>());
	}

	@SuppressWarnings("unchecked")
	private static <@NonNull T> T deepClone(final T obj, final Map<Object, @Nullable Object> clones) {
		final Object clone = clones.get(obj);

		if (clone != null)
			return (T) clone;

		if (obj instanceof List<?>) {
			var list = (List<T>) obj;
			final var listClone = shallowClone(list, () -> new ArrayList<>(list));
			clones.put(list, listClone);
			listClone.replaceAll(v -> deepCloneNullable(v, clones));
			return (T) listClone;
		}

		if (obj instanceof Set<?>) {
			var set = (Set<T>) obj;
			final var setClone = (Set<@Nullable Object>) shallowClone(set, HashSet::new);
			clones.put(set, setClone);
			setClone.clear();
			for (final var e : set) {
				setClone.add(deepCloneNullable(e, clones));
			}
			return (T) setClone;
		}

		if (obj instanceof Map<?, ?>) {
			var map = (Map<?, T>) obj;
			final var mapClone = shallowClone(map, () -> new HashMap<>(map));
			clones.put(map, mapClone);
			mapClone.replaceAll((k, v) -> deepCloneNullable(v, clones));
			return (T) mapClone;
		}

		if (obj.getClass().isArray()) {
			final int len = Array.getLength(obj);
			final var arrayType = obj.getClass().getComponentType();
			final var arrayClone = Array.newInstance(arrayType, len);
			clones.put(obj, arrayClone);
			for (int i = 0; i < len; i++) {
				Array.set(arrayClone, i, deepCloneNullable(Array.get(obj, i), clones));
			}
			return (T) arrayClone;
		}

		final var shallowClone = shallowClone(obj, () -> obj);
		clones.put(obj, shallowClone);
		return obj;
	}

	@Nullable
	private static <@Nullable T> T deepCloneNullable(final T obj, final Map<Object, @Nullable Object> clones) {
		if (obj == null) {
			return null;
		}
		return deepClone(obj, clones);
	}

	@SuppressWarnings("unchecked")
	private static <@NonNull T> T shallowClone(final T obj, final Supplier<T> fallback) {
		if (obj instanceof Cloneable) {
			try {
				final var cloneMethod = CLONE_METHODS_CACHE.computeIfAbsent(obj.getClass(), cls -> {
					try {
						return Optional.of(cls.getMethod("clone"));
					} catch (final Exception ex) {
						return Optional.empty();
					}
				});
				if (cloneMethod.isPresent()) {
					return (T) cloneMethod.get().invoke(obj);
				}
			} catch (final Exception ex) {
				ex.printStackTrace();
			}
		}
		return fallback.get();
	}

	private ObjectCloner() {
	}
}
