// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StuckKeyWordTest {
    @Test fun `long run of one character is a stuck key`() {
        assertTrue(InputLogic.isStuckKeyWord("kkkk"))
        assertTrue(InputLogic.isStuckKeyWord("kkkkkkkkkkkk"))
        assertTrue(InputLogic.isStuckKeyWord("hellokkkkk"))
        assertTrue(InputLogic.isStuckKeyWord("هههه"))
    }

    @Test fun `real words are not a stuck key`() {
        assertFalse(InputLogic.isStuckKeyWord("kkk"))
        assertFalse(InputLogic.isStuckKeyWord("committee"))
        assertFalse(InputLogic.isStuckKeyWord("Schifffahrt"))
        assertFalse(InputLogic.isStuckKeyWord("kkkKkkk"))
        assertFalse(InputLogic.isStuckKeyWord(""))
    }

    @Test fun `supplementary characters count as one character`() {
        assertFalse(InputLogic.isStuckKeyWord("𝒜𝒜𝒜"))
        assertTrue(InputLogic.isStuckKeyWord("𝒜𝒜𝒜𝒜"))
    }
}
