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

  const groupWorlds = (list) => {
    const worlds = Array.from(list.children).filter((child) => child.tagName === "FIELDSET");

    worlds.forEach((world) => {
      const legend = world.querySelector(":scope > legend");
      if (!legend) return;

      const originalName = world.dataset.stemcraftWorldName || legend.textContent.trim();
      if (!originalName) return;
      world.dataset.stemcraftWorldName = originalName;

      const renderer = world.querySelector(":scope > input.renderer:only-of-type");
      if (renderer && legend.dataset.stemcraftClickable !== "true") {
        legend.dataset.stemcraftClickable = "true";
        legend.setAttribute("role", "button");
        legend.setAttribute("tabindex", "0");
        legend.addEventListener("click", () => renderer.click());
        legend.addEventListener("keydown", (event) => {
          if (event.key !== "Enter" && event.key !== " ") return;
          event.preventDefault();
          renderer.click();
        });
      }

      const [groupName, ...remainingWords] = originalName.split(/\s+/);
      const groupKey = groupName.toLocaleLowerCase();
      const itemName = remainingWords.join(" ").replace(/^[-–—:|/]+\s*/, "").trim();
      let group = Array.from(list.querySelectorAll(":scope > .stemcraft-world-group"))
        .find((candidate) => candidate.dataset.groupKey === groupKey);

      if (!group) {
        group = document.createElement("section");
        group.className = "stemcraft-world-group";
        group.dataset.groupKey = groupKey;

        const heading = document.createElement("h3");
        heading.className = "stemcraft-world-group__heading";
        heading.textContent = groupName;
        group.appendChild(heading);
        list.appendChild(group);
      }

      legend.textContent = itemName || originalName;
      group.appendChild(world);
    });

    list.querySelectorAll(":scope > .stemcraft-world-group").forEach((group) => {
      if (!group.querySelector(":scope > fieldset")) group.remove();
    });
  };

  const installWorldGroups = () => {
    const list = document.querySelector("#sidebar__worlds > fieldset.menu");
    if (!list || list.dataset.stemcraftGrouped === "true") return Boolean(list);

    list.dataset.stemcraftGrouped = "true";
    groupWorlds(list);
    new MutationObserver(() => groupWorlds(list)).observe(list, {childList: true, subtree: true});
    return true;
  };

  const installTheme = () => {
    installBrand();
    if (installWorldGroups()) return;

    const observer = new MutationObserver(() => {
      if (installWorldGroups()) observer.disconnect();
    });
    observer.observe(document.body, {childList: true, subtree: true});
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", installTheme, {once: true});
  } else {
    installTheme();
  }
})();
