package no.nav.helse.utbetalingslinjer

enum class Utbetalingstatus(private val tilstand: Utbetaling.Tilstand) {
    NY(Utbetaling.Ny),
    IKKE_UTBETALT(Utbetaling.Ubetalt),
    IKKE_GODKJENT(Utbetaling.IkkeGodkjent),
    GODKJENT(Utbetaling.Godkjent),
    SENDT(Utbetaling.Sendt),
    OVERFØRT(Utbetaling.Overført),
    UTBETALT(Utbetaling.Utbetalt),
    GODKJENT_UTEN_UTBETALING(Utbetaling.GodkjentUtenUtbetaling),
    UTBETALING_FEILET(Utbetaling.UtbetalingFeilet),
    ANNULLERT(Utbetaling.Annullert),
    FORKASTET(Utbetaling.Forkastet);

    internal fun tilTilstand() = tilstand

    internal companion object {
        fun fraTilstand(tilstand: Utbetaling.Tilstand) =
            values().first { it.tilstand == tilstand }
    }
}
