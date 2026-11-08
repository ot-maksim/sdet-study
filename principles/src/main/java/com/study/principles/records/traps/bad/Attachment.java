package com.study.principles.records.traps.bad;

/**
 * TRAP: an array component. The generated members treat arrays by <b>reference</b>.
 *
 * <h2>Three problems at once</h2>
 * <ol>
 *   <li>{@code equals}: two attachments with byte-for-byte identical content are <b>not</b> equal
 *       (arrays inherit {@code Object.equals}), so {@code assertThat(downloaded).isEqualTo(uploaded)}
 *       fails for a correct file;</li>
 *   <li>{@code toString}: prints {@code [B@1b6d3586} — useless in a failure message;</li>
 *   <li>mutability: arrays cannot be made read-only, so both the caller's array and {@code content()}
 *       are back doors into the "immutable" record (the same trap as a {@code List} component, see
 *       {@code invariants.record.ShallowResult}).</li>
 * </ol>
 *
 * @param name    file name
 * @param content raw bytes — compared, printed and stored by reference
 * @see com.study.principles.records.traps.good.Attachment the fixed version
 */
public record Attachment(String name, byte[] content) {
}
