// Photos used by the site itself (home banner, category tiles, seller banner).
// All are free-licence Unsplash photos (https://unsplash.com/license), linked by
// photo id and resized by Unsplash's image CDN.
const unsplash = (id, width) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${width}&q=75`

export const HERO_IMAGE = unsplash('1747192904662-e03e8da1e0ab', 1600) // building under construction
export const SELLER_IMAGE = unsplash('1764116858643-8519de606248', 1400) // workers loading timber onto a truck
export const STOCK_IMAGE = unsplash('1778830355855-58ffa19781e1', 800) // stacked timber at a yard
export const STOCK_IMAGE_LARGE = unsplash('1778830355855-58ffa19781e1', 1200)
export const CALCULATOR_IMAGE = unsplash('1534503442463-e0ddba45cf4c', 800) // grey floor tiles

export const CATEGORY_IMAGES = {
  TILES: unsplash('1523350165414-082d792c4bcc', 640),
  PAINT: unsplash('1643822308521-1da534425d82', 640),
  PLYWOOD: unsplash('1690768162439-7ca7a1813038', 640),
  FITTINGS: unsplash('1610278764397-388d11c35ddb', 640),
  FALSE_CEILING: unsplash('1582203423341-64b918240e25', 640),
  WATERPROOFING: unsplash('1674485169641-bcb2bf6f1df9', 640),
  INTERIOR: unsplash('1616046229478-9901c5536a45', 640),
}
