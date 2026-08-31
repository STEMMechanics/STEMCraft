(() => {
  "use strict";

  document.title = "STEMCraft Map";

  const installBrand = () => {
    if (document.querySelector(".stemcraft-map-brand")) return;

    const brand = document.createElement("a");
    brand.className = "stemcraft-map-brand";
    brand.href = "https://play.stemcraft.com.au/";
    brand.setAttribute("aria-label", "Visit the STEMCraft website");
    brand.innerHTML = `
      <img src="images/stemcraft-logo.png" alt="STEMCraft">
      <span class="stemcraft-map-brand__copy">
        <span class="stemcraft-map-brand__title">Live World Map</span>
        <span class="stemcraft-map-brand__subtitle">Explore · Build · Discover</span>
      </span>`;
    document.body.appendChild(brand);
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", installBrand, {once: true});
  } else {
    installBrand();
  }
})();
