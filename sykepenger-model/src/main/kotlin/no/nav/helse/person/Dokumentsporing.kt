package no.nav.helse.person

import java.util.UUID

data class Dokumentsporing private constructor(private val id: UUID, private val type: Type) {

    companion object {
        internal fun sykmelding(id: UUID) = Dokumentsporing(id, Type.Sykmelding)
        internal fun søknad(id: UUID) = Dokumentsporing(id, Type.Søknad)
        internal fun inntektsmelding(id: UUID) = Dokumentsporing(id, Type.Inntektsmelding)
        internal fun overstyrTidslinje(id: UUID) = Dokumentsporing(id, Type.OverstyrTidslinje)
        internal fun overstyrInntekt(id: UUID) = Dokumentsporing(id, Type.OverstyrInntekt)
        internal fun overstyrRefusjon(id: UUID) = Dokumentsporing(id, Type.OverstyrRefusjon)
        internal fun overstyrArbeidsgiveropplysninger(id: UUID) = Dokumentsporing(id, Type.OverstyrArbeidsgiveropplysninger)
        internal fun overstyrArbeidsforhold(id: UUID) = Dokumentsporing(id, Type.OverstyrArbeidsforhold)

        internal fun Iterable<Dokumentsporing>.toMap() = associate { it.id to it.type }
        internal fun Iterable<Dokumentsporing>.ider() = map { it.id }.toSet()
        internal fun Iterable<Dokumentsporing>.søknadIder() = filter { it.type == Type.Søknad }.map { it.id }.toSet()
        internal fun Map<UUID, Type>.tilSporing() = map { Dokumentsporing(it.key, it.value) }.toSet()
    }

    internal enum class Type {
        Sykmelding,
        Søknad,
        Inntektsmelding,
        OverstyrTidslinje,
        OverstyrInntekt,
        OverstyrRefusjon,
        OverstyrArbeidsgiveropplysninger,
        OverstyrArbeidsforhold,
    }

    internal fun toMap() = mapOf(id.toString() to type.name)
}

