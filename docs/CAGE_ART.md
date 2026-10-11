# Cage interface art

The interface uses native code-drawn Minecraft-style gray beveled panels and
18-pixel recessed inventory cells. The original Rats breeding-lantern item icon
and the game's existing item/upgrades art are rendered without modifications.
This preserves the original Cage models, decorations and texture palette; no
new raster texture or external art dependency was necessary.

Layout: six paginated residents on the left (M/F, * for babies), selected rat
details and a read-only 15-cell inspection panel on the right. Inspection order:
main-hand item, hat, banner, three upgrade slots, nine additional cargo slots.
Names and actions sit below; the selected breeding partner is shown separately.
English and Brazilian Portuguese labels are provided.

Source: `client/gui/RatCageScreen.java`. A native client screenshot/layout and
interaction review is still pending: computer-use reported the Mac locked.
